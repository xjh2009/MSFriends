package dev.msf.friends.p2p;

import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.client.SignalingServiceClient;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.webrtc.RtcHandshake;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * FriendJoinHandler - Java 8 compatible version.
 */
public final class FriendJoinHandler {
    private static final Logger LOGGER = Logging.get();
    public static final long JOIN_REQUEST_TIMEOUT_MINUTES = 1L;

    public enum OutgoingJoinState { NONE, AWAITING_HOST_ACCEPT, CONNECTING, CONNECTED }

    private static class OutgoingJoinRequest {
        final String sessionId;
        final CompletableFuture<Void> result;
        final AtomicBoolean sdpStarted;
        OutgoingJoinRequest(String sessionId, CompletableFuture<Void> result, AtomicBoolean sdpStarted) {
            this.sessionId = sessionId; this.result = result; this.sdpStarted = sdpStarted;
        }
    }

    private final MinecraftBridge bridge;
    private final SignalingServiceClient signaling;
    private final P2PManager manager;
    private final Supplier<PlayerSocialManager> socialSupplier;
    private final ConcurrentHashMap<UUID, String> incomingJoinRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, OutgoingJoinRequest> outgoingJoinRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> acceptedAwaitingOffer = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Runnable> joinStateListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;

    public FriendJoinHandler(MinecraftBridge bridge, SignalingServiceClient signaling,
                             P2PManager manager, Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.signaling = signaling;
        this.manager = manager;
        this.socialSupplier = socialSupplier;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FriendJoin-Timer");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public CompletableFuture<Void> joinPlayer(String peerPmid) {
        final UUID parsed;
        try { parsed = UUID.fromString(peerPmid); }
        catch (IllegalArgumentException e) {
            LOGGER.warn("[P2P][join] supplied PMID is not a valid UUID");
            return failedFuture(e);
        }
        OutgoingJoinRequest existing = outgoingJoinRequests.get(parsed);
        if (existing != null) return existing.result;
        if (manager.hasHandshake(parsed)) return CompletableFuture.completedFuture(null);

        final String signalingSessionId = UUID.randomUUID().toString();
        final CompletableFuture<Void> result = new CompletableFuture<>();
        final OutgoingJoinRequest request = new OutgoingJoinRequest(signalingSessionId, result, new AtomicBoolean());
        OutgoingJoinRequest raced = outgoingJoinRequests.putIfAbsent(parsed, request);
        if (raced != null) return raced.result;

        result.whenComplete(new java.util.function.BiConsumer<Void, Throwable>() {
            @Override public void accept(Void u, Throwable t) {
                if (outgoingJoinRequests.remove(parsed, request)) {
                    notifyJoinStateChanged();
                    manager.maybeDisconnectSignaling();
                }
            }
        });
        notifyJoinStateChanged();
        manager.ensureSignalingConnected();
        scheduler.schedule(new Runnable() {
            @Override public void run() {
                if (!request.sdpStarted.get() && result.completeExceptionally(new TimeoutException("Join request timed out"))) {
                    showJoinInviteExpiredToast(parsed);
                }
            }
        }, JOIN_REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        signaling.sendClientMessage(parsed, SignalingMessage.joinRequest(signalingSessionId, bridge.profileId()))
                .whenComplete(new java.util.function.BiConsumer<Void, Throwable>() {
                    @Override public void accept(Void u, Throwable error) {
                        if (error != null) result.completeExceptionally(error);
                    }
                });
        return result;
    }

    public boolean hasIncomingJoinRequest(UUID peerPmid) { return incomingJoinRequests.containsKey(peerPmid); }
    public boolean hasOutgoingJoinRequests() { return !outgoingJoinRequests.isEmpty(); }

    public OutgoingJoinState outgoingJoinState(UUID peerPmid) {
        OutgoingJoinRequest pending = outgoingJoinRequests.get(peerPmid);
        if (pending != null) {
            return pending.sdpStarted.get() ? OutgoingJoinState.CONNECTING : OutgoingJoinState.AWAITING_HOST_ACCEPT;
        }
        RtcHandshake hs = manager.getHandshake(peerPmid);
        return (hs != null && hs.isInitiator()) ? OutgoingJoinState.CONNECTED : OutgoingJoinState.NONE;
    }

    public UUID connectingOutgoingJoinPmid() {
        for (Map.Entry<UUID, OutgoingJoinRequest> e : outgoingJoinRequests.entrySet()) {
            if (e.getValue().sdpStarted.get()) return e.getKey();
        }
        return null;
    }

    public Map<UUID, String> incomingJoinRequestsView() {
        return Collections.unmodifiableMap(incomingJoinRequests);
    }

    public void acceptIncomingJoinRequest(UUID peerPmid) {
        String sessionId = incomingJoinRequests.remove(peerPmid);
        if (sessionId == null) return;
        notifyJoinStateChanged();
        if (manager.isHostingP2P() && socialSupplier.get().isFriendsPmid(peerPmid)) {
            sendJoinAccepted(peerPmid, sessionId);
        } else {
            signaling.sendClientMessage(peerPmid, SignalingMessage.joinRejected(sessionId))
                    .exceptionally(t -> null);
        }
    }

    public void rejectIncomingJoinRequest(UUID peerPmid) {
        String sessionId = incomingJoinRequests.remove(peerPmid);
        if (sessionId != null) {
            acceptedAwaitingOffer.remove(peerPmid, sessionId);
            signaling.sendClientMessage(peerPmid, SignalingMessage.joinRejected(sessionId))
                    .exceptionally(t -> null);
            notifyJoinStateChanged();
        }
        RtcHandshake existing = manager.getHandshake(peerPmid);
        if (existing != null && !existing.isInitiator()) existing.abort("join request rejected");
    }

    public CompletableFuture<Void> declineInvite(UUID hostPmid) {
        manager.ensureSignalingConnected();
        return signaling.sendClientMessage(hostPmid, SignalingMessage.inviteDeclined())
                .whenComplete(new java.util.function.BiConsumer<Void, Throwable>() {
                    @Override public void accept(Void u, Throwable t) { manager.maybeDisconnectSignaling(); }
                });
    }

    public boolean consumeAcceptedJoinRequest(UUID peerPmid, String sessionId) {
        return acceptedAwaitingOffer.remove(peerPmid, sessionId);
    }

    public void clearHostJoinRequestState() {
        if (!incomingJoinRequests.isEmpty()) {
            incomingJoinRequests.clear();
            notifyJoinStateChanged();
        }
        acceptedAwaitingOffer.clear();
    }

    public void cancelOutgoingJoins() {
        for (Map.Entry<UUID, OutgoingJoinRequest> entry : outgoingJoinRequests.entrySet()) {
            if (outgoingJoinRequests.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().result.completeExceptionally(new CancellationException("join request cancelled"));
                notifyJoinStateChanged();
            }
        }
    }

    public void shutdown() {
        incomingJoinRequests.clear();
        for (OutgoingJoinRequest req : outgoingJoinRequests.values()) {
            req.result.completeExceptionally(new CancellationException("shutdown"));
        }
        outgoingJoinRequests.clear();
        acceptedAwaitingOffer.clear();
        scheduler.shutdownNow();
        notifyJoinStateChanged();
    }

    public void addJoinStateListener(Runnable r) { joinStateListeners.add(r); }
    public void removeJoinStateListener(Runnable r) { joinStateListeners.remove(r); }

    void notifyJoinStateChanged() {
        bridge.executeOnClientThread(new Runnable() {
            @Override public void run() {
                for (Runnable r : joinStateListeners) r.run();
            }
        });
    }

    private CompletableFuture<Void> sendJoinAccepted(UUID peerPmid, String sessionId) {
        acceptedAwaitingOffer.put(peerPmid, sessionId);
        return signaling.sendClientMessage(peerPmid, SignalingMessage.joinAccepted(sessionId));
    }

    private void showJoinInviteExpiredToast(UUID peerPmid) {
        bridge.executeOnClientThread(new Runnable() {
            @Override public void run() {
                UUID peerProfileId = socialSupplier.get().getPresenceHandler().getProfileIdFromPmid(peerPmid);
                if (peerProfileId == null) return;
                for (PlayerSocialManager.PlayerData p : socialSupplier.get().getFriends()) {
                    if (p.id().equals(peerProfileId)) {
                        NotificationPrefs prefs = NotificationPrefs.get();
                        if (prefs.notifyJoinRequest) bridge.notifyToast("friend.join_expired", p.name(), p.id());
                        break;
                    }
                }
            }
        });
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable t) {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(t);
        return f;
    }
}
