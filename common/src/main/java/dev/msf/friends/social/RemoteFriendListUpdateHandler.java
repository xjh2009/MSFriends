package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.FriendData;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

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
 * Strict 26.2 port of {@code RemoteFriendListUpdateHandler}.
 *
 * <p>The original switches its poll interval based on whether the
 * {@code FriendsOverlayScreen} is currently shown; in headless mode we
 * approximate "foreground" by deferring to a callback supplied via
 * {@link #setForegroundDecider} (the HTTP layer flips it when the web UI
 * has an open SSE subscription).
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
    private volatile Set<FriendDto> knownFriends  = new HashSet<>();
    private volatile Set<FriendDto> knownIncoming = new HashSet<>();
    private volatile Set<FriendDto> knownOutgoing = new HashSet<>();
    @Nullable private ScheduledFuture<?> scheduledTick;

    private volatile java.util.function.BooleanSupplier foregroundDecider = () -> false;

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
        AtomicReference<FriendData> friendData = new AtomicReference<>(FriendData.empty());
        boolean shouldNotifyListeners = false;
        try {
            FriendsService.ResultCode resultCode = friendsService.getFriendData(friendData::set);
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
        return switch (r) {
            case TEMPORARY_UNAVAILABLE, FORBIDDEN, SERVICE_NOT_AVAILABLE, TOO_MANY_REQUESTS -> State.TEMPORARY_UNAVAILABLE;
            case CONNECTION_ISSUE -> State.CONNECTION_ISSUE;
            case UPGRADE_NEEDED -> State.UPGRADE_NEEDED;
            case UNKNOWN_PROFILE -> State.USER_MAY_LACK_ACTIVE_PROFILE;
            case GENERIC_ERROR, ERROR -> State.GENERIC_ERROR;
            case SUCCESS -> State.SUCCESS;
        };
    }

    private void notifyListeners() {
        if (updateListeners.isEmpty()) return;
        LOGGER.debug("Notifying {} Friends List update listeners", updateListeners.size());
        bridge.executeOnClientThread(() -> {
            for (Runnable r : updateListeners) {
                try { r.run(); }
                catch (Throwable t) { LOGGER.warn("Friends List callback failed", t); }
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
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            scheduler.execute(() -> {
                try { runUpdateFriendDataInternal(); }
                finally { future.complete(null); }
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
            scheduledTick = scheduler.scheduleWithFixedDelay(this::runBackgroundTick, 0L, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
