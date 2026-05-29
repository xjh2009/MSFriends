package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.FriendData;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Polling-based friend list updater (1.7.10 / Java 8 port).
 */
public final class RemoteFriendListUpdateHandler {
    private static final Logger LOGGER = Logging.get(RemoteFriendListUpdateHandler.class);
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
        void emit(MinecraftBridge bridge, String playerName, UUID profileId);
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
    private volatile ScheduledFuture<?> scheduledTick;
    private volatile BooleanSupplier foregroundDecider = new BooleanSupplier() {
        @Override public boolean getAsBoolean() { return false; }
    };

    public RemoteFriendListUpdateHandler(FriendsService friendsService, MinecraftBridge bridge) {
        this.friendsService = friendsService;
        this.bridge = bridge;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FriendsPoller");
            t.setDaemon(true);
            return t;
        });
    }

    public void setForegroundDecider(BooleanSupplier decider) {
        this.foregroundDecider = decider;
    }

    public FriendData getLatestFriendData() { return latestFriendData; }
    public State getState() { return state; }

    public void addUpdateListener(Runnable r) { updateListeners.add(r); }
    public void removeUpdateListener(Runnable r) { updateListeners.remove(r); }

    public void start() {
        enabled.set(true);
        if (scheduledTick == null) {
            scheduledTick = scheduler.scheduleAtFixedRate(new Runnable() {
                @Override public void run() { tick(); }
            }, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        enabled.set(false);
        ScheduledFuture<?> f = scheduledTick;
        if (f != null) { f.cancel(false); scheduledTick = null; }
    }

    private void tick() {
        if (!enabled.get() || updateInProgress.get()) return;
        long now = System.nanoTime();
        long interval = foregroundDecider.getAsBoolean() ? FOREGROUND_INTERVAL_NANOS : BACKGROUND_INTERVAL_NANOS;
        if (now - lastUpdateNanos < interval) return;
        lastUpdateNanos = now;
        updateInProgress.set(true);
        try {
            FriendData data = friendsService.getFriendData();
            latestFriendData = data;
            state = State.SUCCESS;
            detectChanges(data);
            fireUpdate();
        } catch (Exception e) {
            LOGGER.error("Friend list poll failed", e);
            state = State.GENERIC_ERROR;
        } finally {
            updateInProgress.set(false);
        }
    }

    private void detectChanges(FriendData data) {
        Set<FriendDto> newFriends = new HashSet<>(data.friends());
        Set<FriendDto> newIncoming = new HashSet<>(data.incomingRequests());
        Set<FriendDto> newOutgoing = new HashSet<>(data.outgoingRequests());

        // Detect new friends
        for (FriendDto f : newFriends) {
            if (!knownFriends.contains(f)) {
                LOGGER.info("New friend: {}", f.name());
            }
        }
        // Detect new incoming requests
        for (FriendDto f : newIncoming) {
            if (!knownIncoming.contains(f)) {
                LOGGER.info("New incoming friend request: {}", f.name());
            }
        }

        knownFriends = newFriends;
        knownIncoming = newIncoming;
        knownOutgoing = newOutgoing;
    }

    private void fireUpdate() {
        for (Runnable r : updateListeners) {
            try { r.run(); } catch (Exception e) { LOGGER.error("Update listener error", e); }
        }
    }

    public CompletableFuture<Void> refreshNow() {
        CompletableFuture<Void> f = new CompletableFuture<>();
        scheduler.execute(new Runnable() {
            @Override public void run() {
                try {
                    lastUpdateNanos = 0L;
                    tick();
                    f.complete(null);
                } catch (Exception e) {
                    f.completeExceptionally(e);
                }
            }
        });
        return f;
    }
}
