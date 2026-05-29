package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.FriendData;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import com.mojang.authlib.yggdrasil.response.FriendsListResponse;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RemoteFriendListUpdateHandler - Java 8 compatible version.
 */
public final class RemoteFriendListUpdateHandler {
    private static final Logger LOGGER = Logging.get();
    private static final long FOREGROUND_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1L);
    private static final long BACKGROUND_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5L);
    private static final long POLL_INTERVAL_SECONDS = 1L;

    public enum State {
        LOADING, UPGRADE_NEEDED, CONNECTION_ISSUE, USER_MAY_LACK_ACTIVE_PROFILE,
        TEMPORARY_UNAVAILABLE, GENERIC_ERROR, SUCCESS
    }

    @FunctionalInterface
    public interface SkinToastEmitter {
        void emit(MinecraftBridge bridge, String playerName, java.util.UUID profileId);
    }

    private final FriendsService friendsService;
    private final MinecraftBridge bridge;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean updateInProgress = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Set<Runnable> updateListeners = new CopyOnWriteArraySet<>();
    private volatile long lastUpdateNanos = 0L;
    private volatile FriendData latestFriendData = FriendData.empty();
    private volatile State state = State.LOADING;
    private volatile Set<FriendDto> knownFriends = new HashSet<>();
    private volatile Set<FriendDto> knownIncoming = new HashSet<>();
    private volatile Set<FriendDto> knownOutgoing = new HashSet<>();
    private volatile ScheduledFuture<?> scheduledTick;
    private volatile boolean foreground = false;

    public RemoteFriendListUpdateHandler(FriendsService friendsService, MinecraftBridge bridge) {
        this.friendsService = friendsService;
        this.bridge = bridge;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Friends List");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void setForeground(boolean fg) { this.foreground = fg; }

    public FriendData getLatestFriendData() { return latestFriendData; }
    public State getState() { return state; }

    public void addUpdateListener(Runnable r)    { updateListeners.add(r); }
    public void removeUpdateListener(Runnable r) { updateListeners.remove(r); }

    private long getUpdateIntervalNanos() {
        return foreground ? FOREGROUND_INTERVAL_NANOS : BACKGROUND_INTERVAL_NANOS;
    }

    private void runBackgroundTick() {
        if (updateInProgress.get() || !enabled.get()) return;
        long now = System.nanoTime();
        if (lastUpdateNanos == 0L || now - lastUpdateNanos >= getUpdateIntervalNanos()) {
            runUpdateFriendDataInternal();
        }
    }

    void runUpdateFriendDataInternal() {
        if (!updateInProgress.compareAndSet(false, true)) {
            LOGGER.debug("Attempted to run Friends List update but update is already in progress");
            return;
        }
        LOGGER.debug("Performing Friends List update");
        boolean shouldNotifyListeners = false;
        try {
            FriendsListResponse response = friendsService.getFriendList();

            State newState = State.SUCCESS;
            State previousState = this.state;
            boolean stateTransition = previousState != newState;
            this.state = newState;

            FriendData data = new FriendData(
                response.friends(),
                response.incomingRequests(),
                response.outgoingRequests()
            );
            this.latestFriendData = data;
            boolean dataChanged = detectChangesAndShowToast(data, previousState);
            shouldNotifyListeners = dataChanged || stateTransition;
        } catch (Throwable t) {
            LOGGER.warn("Failed to update friend data", t);
            this.state = State.GENERIC_ERROR;
            shouldNotifyListeners = true;
        } finally {
            updateInProgress.set(false);
            lastUpdateNanos = System.nanoTime();
            if (shouldNotifyListeners) notifyListeners();
        }
    }

    private void notifyListeners() {
        if (updateListeners.isEmpty()) return;
        LOGGER.debug("Notifying {} Friends List update listeners", updateListeners.size());
        bridge.executeOnClientThread(new Runnable() {
            @Override public void run() {
                for (Runnable r : updateListeners) {
                    try { r.run(); }
                    catch (Throwable t) { LOGGER.warn("Friends List callback failed", t); }
                }
            }
        });
    }

    private boolean detectChangesAndShowToast(FriendData data, State previousState) {
        Set<FriendDto> currentFriends = new HashSet<>(data.friends());
        Set<FriendDto> currentIncoming = new HashSet<>(data.incomingRequests());
        Set<FriendDto> currentOutgoing = new HashSet<>(data.outgoingRequests());

        if (previousState != State.SUCCESS) {
            knownFriends = currentFriends;
            knownIncoming = currentIncoming;
            knownOutgoing = currentOutgoing;
            return true;
        }

        if (!isInGameAndToastsDisabled()) {
            for (FriendDto f : currentFriends) {
                if (!knownFriends.contains(f)) {
                    bridge.notifyToast("friend.added", f.name(), f.profileId());
                }
            }
            for (FriendDto f : currentIncoming) {
                if (!knownIncoming.contains(f) && !currentFriends.contains(f)) {
                    bridge.notifyToast("friend.request_received", f.name(), f.profileId());
                }
            }
        }

        boolean hasChanges = !knownFriends.equals(currentFriends)
                || !knownIncoming.equals(currentIncoming)
                || !knownOutgoing.equals(currentOutgoing);
        knownFriends = currentFriends;
        knownIncoming = currentIncoming;
        knownOutgoing = currentOutgoing;
        return hasChanges;
    }

    private boolean isInGameAndToastsDisabled() {
        return bridge.inLevel() && !bridge.inGameNotificationsEnabled();
    }

    public CompletableFuture<Void> forceUpdate() {
        if (!enabled.get() || scheduler.isShutdown()) return CompletableFuture.completedFuture(null);
        final CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            scheduler.execute(new Runnable() {
                @Override public void run() {
                    try { runUpdateFriendDataInternal(); }
                    finally { future.complete(null); }
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("Failed to schedule forced Friends List update", t);
            future.complete(null);
        }
        return future;
    }

    public synchronized void start() {
        if (scheduler.isShutdown()) return;
        if (!enabled.compareAndSet(false, true)) return;
        if (scheduledTick == null || scheduledTick.isCancelled() || scheduledTick.isDone()) {
            scheduledTick = scheduler.scheduleWithFixedDelay(new Runnable() {
                @Override public void run() { runBackgroundTick(); }
            }, 0L, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    public synchronized void stop() {
        enabled.set(false);
        if (scheduledTick != null) { scheduledTick.cancel(false); scheduledTick = null; }
    }

    public synchronized void close() {
        stop();
        scheduler.shutdownNow();
    }
}
