package dev.msf.friends.p2p;

import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.client.SignalingServiceClient;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.webrtc.RtcHandshake;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Ported from 26.2 FriendJoinHandler. Java 8 compatible. */
public final class FriendJoinHandler {
    private static final Logger LOGGER = Logging.get();
    public static final long JOIN_REQUEST_TIMEOUT_MINUTES = 1L;

    public enum OutgoingJoinState { NONE, AWAITING_HOST_ACCEPT, CONNECTING, CONNECTED }

    /** Replaces the original Java 14+ record. */
    private static final class OutgoingJoinRequest {
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
    private final ConcurrentHashMap<UUID, String> incomingJoinRequests = new ConcurrentHashMap<UUID, String>();
    private final ConcurrentHashMap<UUID, OutgoingJoinRequest> outgoingJoinRequests = new ConcurrentHashMap<UUID, OutgoingJoinRequest>();
    private final ConcurrentHashMap<UUID, String> acceptedAwaitingOffer = new ConcurrentHashMap<UUID, String>();
    private final CopyOnWriteArrayList<Runnable> joinStateListeners = new CopyOnWriteArrayList<Runnable>();
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "P2P-JoinTimeout");
        t.setDaemon(true);
        return t;
    });

    private final SignalingServiceClient.ConnectionListener connectionListener = new SignalingServiceClient.ConnectionListener() {
        @Override public void onSignalingError(UUID peerPmid, SignalingException cause) {
            if (peerPmid == null) return;
            OutgoingJoinRequest pending = outgoingJoinRequests.get(peerPmid);
            if (pending != null) pending.result.completeExceptionally(cause);
        }
        @Override public void onSignalingDisconnected() {}
    };

    public FriendJoinHandler(MinecraftBridge bridge, SignalingServiceClient signaling,
                             P2PManager manager, Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.signaling = signaling;
        this.manager = manager;
        this.socialSupplier = socialSupplier;
        signaling.setFriendJoinHandler(new SignalingServiceClient.FriendJoinHandler() {
            @Override public void handle(UUID fromPmid, SignalingMessage.FriendJoin message) {
                FriendJoinHandler.this.handle(fromPmid, message);
            }
        });
        signaling.addConnectionListener(connectionListener);
    }

    // -------- public API --------

    public CompletableFuture<Void> joinPlayer(String peerPmid) {
        UUID parsed;
        try { parsed = UUID.fromString(peerPmid); }
        catch (IllegalArgumentException e) {
            LOGGER.warn("[P2P][join] supplied PMID is not a valid UUID");
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return f;
        }

        OutgoingJoinRequest existing = outgoingJoinRequests.get(parsed);
        if (existing != null) return existing.result;
        if (manager.hasHandshake(parsed)) return CompletableFuture.completedFuture(null);

        String signalingSessionId = UUID.randomUUID().toString();
        CompletableFuture<Void> result = new CompletableFuture<Void>();
        OutgoingJoinRequest request = new OutgoingJoinRequest(signalingSessionId, result, new AtomicBoolean());
        OutgoingJoinRequest raced = outgoingJoinRequests.putIfAbsent(parsed, request);
        if (raced != null) return raced.result;

        result.whenComplete((u, t) -> {
            if (outgoingJoinRequests.remove(parsed, request)) {
                notifyJoinStateChanged();
                manager.maybeDisconnectSignaling();
            }
        });
        notifyJoinStateChanged();
        manager.ensureSignalingConnected();
        timeoutScheduler.schedule(() -> {
            if (!request.sdpStarted.get()
                    && result.completeExceptionally(new TimeoutException("Join request timed out"))) {
                showJoinInviteExpiredToast(parsed);
            }
        }, JOIN_REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        signaling.sendClientMessage(parsed, SignalingMessage.joinRequest(signalingSessionId, bridge.profileId()))
                .whenComplete((u, error) -> { if (error != null) result.completeExceptionally(error); });
        return result;
    }

    public boolean hasIncomingJoinRequest(UUID peerPmid) { return incomingJoinRequests.containsKey(peerPmid); }
    public boolean hasOutgoingJoinRequests()             { return !outgoingJoinRequests.isEmpty(); }

    public OutgoingJoinState outgoingJoinState(UUID peerPmid) {
        OutgoingJoinRequest pending = outgoingJoinRequests.get(peerPmid);
        if (pending != null) {
            return pending.sdpStarted.get() ? OutgoingJoinState.CONNECTING : OutgoingJoinState.AWAITING_HOST_ACCEPT;
        }
        RtcHandshake hs = manager.getHandshake(peerPmid);
        return (hs != null && hs.isInitiator()) ? OutgoingJoinState.CONNECTED : OutgoingJoinState.NONE;
    }

    public UUID connectingOutgoingJoinPmid() {
        for (java.util.Map.Entry<UUID, OutgoingJoinRequest> e : outgoingJoinRequests.entrySet()) {
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
            sendJoinAccepted(peerPmid, sessionId).exceptionally(t -> null);
        } else {
            signaling.sendClientMessage(peerPmid, SignalingMessage.joinRejected(sessionId))
                    .exceptionally(err -> { LOGGER.warn("[P2P][host] Failed to reject pending join request for session {}: {}", sessionId, err.getMessage()); return null; });
        }
    }

    public void rejectIncomingJoinRequest(UUID peerPmid) {
        String sessionId = incomingJoinRequests.remove(peerPmid);
        if (sessionId != null) {
            acceptedAwaitingOffer.remove(peerPmid, sessionId);
            signaling.sendClientMessage(peerPmid, SignalingMessage.joinRejected(sessionId))
                    .exceptionally(err -> { LOGGER.warn("[P2P][host] Failed to send join rejection for session {}: {}", sessionId, err.getMessage()); return null; });
            notifyJoinStateChanged();
        }
        RtcHandshake existing = manager.getHandshake(peerPmid);
        if (existing != null && !existing.isInitiator()) existing.abort("join request rejected");
    }

    public CompletableFuture<Void> declineInvite(UUID hostPmid) {
        manager.ensureSignalingConnected();
        return signaling.sendClientMessage(hostPmid, SignalingMessage.inviteDeclined())
                .whenComplete((u, t) -> manager.maybeDisconnectSignaling())
                .exceptionally(err -> {
                    LOGGER.warn("[P2P][join] Failed to decline invite: {}", err.getMessage());
                    return null;
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
        for (java.util.Map.Entry<UUID, OutgoingJoinRequest> e : outgoingJoinRequests.entrySet()) {
            OutgoingJoinRequest request = e.getValue();
            if (outgoingJoinRequests.remove(e.getKey(), request)) {
                request.result.completeExceptionally(new CancellationException("join request cancelled"));
                notifyJoinStateChanged();
            }
        }
    }

    public void shutdown() {
        signaling.removeConnectionListener(connectionListener);
        incomingJoinRequests.clear();
        for (OutgoingJoinRequest req : outgoingJoinRequests.values()) {
            req.result.completeExceptionally(new CancellationException("shutdown"));
        }
        outgoingJoinRequests.clear();
        acceptedAwaitingOffer.clear();
        notifyJoinStateChanged();
    }

    public void addJoinStateListener(Runnable r)    { joinStateListeners.add(r); }
    public void removeJoinStateListener(Runnable r) { joinStateListeners.remove(r); }

    void notifyJoinStateChanged() {
        bridge.executeOnClientThread(new Runnable() {
            @Override public void run() {
                for (Runnable r : joinStateListeners) r.run();
            }
        });
    }

    // -------- inbound dispatch --------

    private void handle(UUID fromPmid, SignalingMessage.FriendJoin msg) {
        if (msg instanceof SignalingMessage.FriendJoin.Request) {
            handleJoinRequest(fromPmid, (SignalingMessage.FriendJoin.Request) msg);
        } else if (msg instanceof SignalingMessage.FriendJoin.Accepted) {
            handleJoinAccepted(fromPmid, ((SignalingMessage.FriendJoin.Accepted) msg).sessionId());
        } else if (msg instanceof SignalingMessage.FriendJoin.Rejected) {
            handleJoinRejected(fromPmid, ((SignalingMessage.FriendJoin.Rejected) msg).sessionId());
        } else if (msg instanceof SignalingMessage.FriendJoin.InviteDeclined) {
            handleInviteDeclined(fromPmid);
        }
    }

    private void handleJoinRequest(UUID fromPmid, SignalingMessage.FriendJoin.Request request) {
        String sessionId = request.sessionId();
        LOGGER.info("[P2P][host] handleJoinRequest fromPmid={} sessionId={} isHosting={}", fromPmid, sessionId, manager.isHostingP2P());
        if (!manager.isHostingP2P()) {
            signaling.sendClientMessage(fromPmid, SignalingMessage.joinRejected(sessionId))
                    .exceptionally(err -> { LOGGER.warn("[P2P][host] Failed to reject join request for session {}: {}", sessionId, err.getMessage()); return null; });
            return;
        }
        PlayerSocialManager social = socialSupplier.get();
        UUID requestProfileId = request.profileId();
        if (requestProfileId != null) {
            social.getPresenceHandler().rememberPmidMapping(requestProfileId, fromPmid);
        }
        boolean isFriend = social.isFriendsPmid(fromPmid);
        boolean isInvited = social.getPresenceHandler().isInvitedPmid(fromPmid);
        if (!isFriend) return;
        if (isInvited) {
            sendJoinAccepted(fromPmid, sessionId).thenRun(new Runnable() {
                @Override public void run() { clearHostInvite(fromPmid); }
            }).exceptionally(t -> null);
            return;
        }
        incomingJoinRequests.put(fromPmid, sessionId);
        notifyJoinStateChanged();
        UUID peerProfileId = social.getPresenceHandler().getProfileIdFromPmid(fromPmid);
        if (peerProfileId == null) return;
        social.getFriends().stream()
                .filter(p -> p.id().equals(peerProfileId))
                .findAny()
                .ifPresent(friend -> bridge.executeOnClientThread(new Runnable() {
                    @Override public void run() {
                        if (NotificationPrefs.get().notifyJoinRequest)
                            bridge.notifyToast("friend.join_request", friend.name(), friend.id());
                    }
                }));
    }

    private void handleJoinAccepted(UUID fromPmid, String sessionId) {
        OutgoingJoinRequest request = outgoingJoinRequests.get(fromPmid);
        if (request == null) return;
        if (!request.sessionId.equals(sessionId)) return;
        if (!request.sdpStarted.compareAndSet(false, true)) return;
        notifyJoinStateChanged();
        if (manager.hasHandshake(fromPmid)) {
            request.result.completeExceptionally(new IllegalStateException("Handshake already in progress"));
            return;
        }
        manager.startHandshake(fromPmid, sessionId).whenComplete((u, error) -> {
            if (error != null) request.result.completeExceptionally(error);
            else { request.result.complete(null); manager.cancelOutgoingJoins(); }
        });
    }

    private void handleJoinRejected(UUID fromPmid, String sessionId) {
        OutgoingJoinRequest request = outgoingJoinRequests.get(fromPmid);
        if (request != null && request.sessionId.equals(sessionId)) {
            request.result.completeExceptionally(new RuntimeException("Join request rejected"));
        }
    }

    private void handleInviteDeclined(UUID fromPmid) {
        if (!socialSupplier.get().isFriendsPmid(fromPmid)) return;
        clearHostInvite(fromPmid);
    }

    private void clearHostInvite(UUID peerPmid) {
        socialSupplier.get().getPresenceHandler().clearInviteForPmid(peerPmid);
    }

    private CompletableFuture<Void> sendJoinAccepted(UUID peerPmid, String sessionId) {
        acceptedAwaitingOffer.put(peerPmid, sessionId);
        return signaling.sendClientMessage(peerPmid, SignalingMessage.joinAccepted(sessionId))
                .exceptionally(err -> {
                    acceptedAwaitingOffer.remove(peerPmid, sessionId);
                    LOGGER.warn("[P2P][host] Failed to send join acceptance for session {}: {}", sessionId, err.getMessage());
                    return null;
                });
    }

    private void showJoinInviteExpiredToast(UUID peerPmid) {
        bridge.executeOnClientThread(new Runnable() {
            @Override public void run() {
                UUID peerProfileId = socialSupplier.get().getPresenceHandler().getProfileIdFromPmid(peerPmid);
                if (peerProfileId == null) return;
                socialSupplier.get().getFriends().stream()
                        .filter(p -> p.id().equals(peerProfileId))
                        .findAny()
                        .ifPresent(friend -> {
                            if (NotificationPrefs.get().notifyJoinRequest)
                                bridge.notifyToast("friend.join_expired", friend.name(), friend.id());
                        });
            }
        });
    }
}
