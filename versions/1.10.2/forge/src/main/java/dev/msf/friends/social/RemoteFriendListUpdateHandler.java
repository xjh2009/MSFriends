package dev.msf.friends.social;

import dev.msf.friends.authlib.FriendsService;
import dev.msf.friends.authlib.response.FriendData;
import dev.msf.friends.authlib.response.FriendDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java 8 port of RemoteFriendListUpdateHandler.
 */
public final class RemoteFriendListUpdateHandler {
    private static final Logger LOGGER = Logging.get();
    private static final long FOREGROUND_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1L);
    private static final long BACKGROUND_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5L);
    private static final long POLL_INTERVAL_SECONDS = 1L;

    public enum State {
        LOADING,
        UPGRADE_NEEDED,
        CONNECTION_ISSUE,
        USER_MAY_LACK_ACTIVE_PROFILE,
        TEMPORARY_UNAVAILABLE,
        GENERIC_ERROR,
        SUCCESS
    }

    private final FriendsService friendsService;
    private final MinecraftBridge bridge;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean updateInProgress = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final CopyOnWriteArraySet<Runnable> updateListeners = new CopyOnWriteArraySet<>();
    private volatile long lastUpdateNanos = 0L;
    private volatile FriendData latestFriendData = FriendData.empty();
    private volatile State state = State.LOADING;
    private volatile Set<FriendDto> knownFriends  = new HashSet<>();
    private volatile Set<FriendDto> knownIncoming = new HashSet<>();
    private volatile Set<FriendDto> knownOutgoing = new HashSet<>();
    private volatile ScheduledFuture<?> scheduledTick;

    private volatile java.util.function.BooleanSupplier foregroundDecider = new java.util.function.BooleanSupplier() {
        @Override public boolean getAsBoolean() { return false; }
    };

    public RemoteFriendListUpdateHandler(FriendsService friendsService, MinecraftBridge bridge) {
        this.friendsService = friendsService;
        this.bridge = bridge;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Friends List");
            t.setDaemon(true);
            return t;
        });
    }

    public void setForegroundDecider(java.util.function.BooleanSupplier decider) {
        this.foregroundDecider = decider;
    }

    public FriendData getLatestFriendData() { return latestFriendData; }
    public State getState() { return state; }

    public void addUpdateListener(Runnable r)    { updateListeners.add(r); }
    public void removeUpdateListener(Runnable r) { updateListeners.remove(r); }

    private long getUpdateIntervalNanos() {
        return foregroundDecider.getAsBoolean() ? FOREGROUND_INTERVAL_NANOS : BACKGROUND_INTERVAL_NANOS;
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
        final AtomicReference<FriendData> friendData = new AtomicReference<>(FriendData.empty());
        boolean shouldNotifyListeners = false;
        try {
            FriendsService.ResultCode resultCode = friendsService.getFriendData(new java.util.function.Consumer<FriendData>() {
                @Override public void accept(FriendData data) { friendData.set(data); }
            });
            State newState = mapResultCodeToState(resultCode);
            State previousState = this.state;
            boolean stateTransition = previousState != newState;
            this.state = newState;
            if (resultCode == FriendsService.ResultCode.SUCCESS) {
                FriendData data = friendData.get();
                this.latestFriendData = data;
                boolean dataChanged = detectChangesAndShowToast(data, previousState);
                shouldNotifyListeners = dataChanged || stateTransition;
                return;
            }
            LOGGER.warn("Friends List update failed with result code: {}", resultCode);
            shouldNotifyListeners = true;
        } catch (Throwable t) {
            LOGGER.warn("Failed to update friend data", t);
            return;
        } finally {
            updateInProgress.set(false);
            lastUpdateNanos = System.nanoTime();
            if (shouldNotifyListeners) notifyListeners();
        }
    }

    private static State mapResultCodeToState(FriendsService.ResultCode r) {
        switch (r) {
            case TEMPORARY_UNAVAILABLE:
            case FORBIDDEN:
            case SERVICE_NOT_AVAILABLE:
            case TOO_MANY_REQUESTS:
                return State.TEMPORARY_UNAVAILABLE;
            case CONNECTION_ISSUE:
                return State.CONNECTION_ISSUE;
            case UPGRADE_NEEDED:
                return State.UPGRADE_NEEDED;
            case UNKNOWN_PROFILE:
                return State.USER_MAY_LACK_ACTIVE_PROFILE;
            case GENERIC_ERROR:
            case ERROR:
                return State.GENERIC_ERROR;
            case SUCCESS:
                return State.SUCCESS;
            default:
                return State.GENERIC_ERROR;
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
        Set<FriendDto> currentFriends  = new HashSet<>(data.friends());
        Set<FriendDto> currentIncoming = new HashSet<>(data.incomingRequests());
        Set<FriendDto> currentOutgoing = new HashSet<>(data.outgoingRequests());

        if (previousState != State.SUCCESS) {
            knownFriends  = currentFriends;
            knownIncoming = currentIncoming;
            knownOutgoing = currentOutgoing;
            return true;
        }

        if (!isInGameAndToastsDisabled()) {
            for (FriendDto f : currentFriends) {
                if (!knownFriends.contains(f)) {
                    if (!knownOutgoing.contains(f) && !knownIncoming.contains(f)) {
                        bridge.notifyToast("friend.added", f.name(), f.profileId());
                    } else {
                        bridge.notifyToast("friend.request_accepted", f.name(), f.profileId());
                    }
                }
            }
            for (FriendDto f : currentIncoming) {
                if (!knownIncoming.contains(f) && !currentFriends.contains(f)) {
                    bridge.notifyToast("friend.request_received", f.name(), f.profileId());
                }
            }
            for (FriendDto f : currentOutgoing) {
                if (!knownOutgoing.contains(f) && !currentFriends.contains(f)) {
                    bridge.notifyToast("friend.request_sent", f.name(), f.profileId());
                }
            }
        }

        boolean hasChanges = !knownFriends.equals(currentFriends)
                || !knownIncoming.equals(currentIncoming)
                || !knownOutgoing.equals(currentOutgoing);
        knownFriends  = currentFriends;
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
        if (scheduler.isShutdown()) {
            LOGGER.warn("Attempted to start Friends List updater but scheduler is already shut down");
            return;
        }
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
