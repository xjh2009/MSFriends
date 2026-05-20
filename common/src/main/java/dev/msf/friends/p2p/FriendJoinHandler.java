package dev.msf.friends.p2p;

import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.client.SignalingServiceClient;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Strict 26.2 port of {@code FriendJoinHandler}, abstracted over a
 * {@link MinecraftBridge} for the few client-side interactions
 * (toasts, friend lookup, "is hosting" check, mainthread execute).
 */
public final class FriendJoinHandler {
    private static final Logger LOGGER = Logging.get();
    public static final long JOIN_REQUEST_TIMEOUT_MINUTES = 1L;

    public enum OutgoingJoinState { NONE, AWAITING_HOST_ACCEPT, CONNECTING, CONNECTED }

    private record OutgoingJoinRequest(String sessionId, CompletableFuture<Void> result, AtomicBoolean sdpStarted) {}

    private final MinecraftBridge bridge;
    private final SignalingServiceClient signaling;
    private final P2PManager manager;
    private final Supplier<PlayerSocialManager> socialSupplier;
    private final ConcurrentHashMap<UUID, String> incomingJoinRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, OutgoingJoinRequest> outgoingJoinRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> acceptedAwaitingOffer = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Runnable> joinStateListeners = new CopyOnWriteArrayList<>();

    private final SignalingServiceClient.ConnectionListener connectionListener = new SignalingServiceClient.ConnectionListener() {
        @Override public void onSignalingError(@Nullable UUID peerPmid, SignalingException cause) {
            if (peerPmid == null) return;
            OutgoingJoinRequest pending = outgoingJoinRequests.get(peerPmid);
            if (pending != null) pending.result().completeExceptionally(cause);
        }
    };

    public FriendJoinHandler(MinecraftBridge bridge, SignalingServiceClient signaling,
                             P2PManager manager, Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.signaling = signaling;
        this.manager = manager;
        this.socialSupplier = socialSupplier;
        signaling.setFriendJoinHandler(this::handle);
        signaling.addConnectionListener(connectionListener);
    }

    // -------- public API --------

    public CompletableFuture<Void> joinPlayer(String peerPmid) {
        UUID parsed;
        try { parsed = UUID.fromString(peerPmid); }
        catch (IllegalArgumentException e) {
            LOGGER.warn("[P2P][join] supplied PMID is not a valid UUID");
            return CompletableFuture.failedFuture(e);
        }

        OutgoingJoinRequest existing = outgoingJoinRequests.get(parsed);
        if (existing != null) return existing.result();
        if (manager.hasHandshake(parsed)) return CompletableFuture.completedFuture(null);

        String signalingSessionId = UUID.randomUUID().toString();
        CompletableFuture<Void> result = new CompletableFuture<>();
        OutgoingJoinRequest request = new OutgoingJoinRequest(signalingSessionId, result, new AtomicBoolean());
        OutgoingJoinRequest raced = outgoingJoinRequests.putIfAbsent(parsed, request);
        if (raced != null) return raced.result();

        result.whenComplete((u, t) -> {
            if (outgoingJoinRequests.remove(parsed, request)) {
                notifyJoinStateChanged();
                manager.maybeDisconnectSignaling();
            }
        });
        notifyJoinStateChanged();
        manager.ensureSignalingConnected();
        CompletableFuture.delayedExecutor(JOIN_REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES).execute(() -> {
            if (!request.sdpStarted().get()
                    && result.completeExceptionally(new TimeoutException("Join request timed out"))) {
                showJoinInviteExpiredToast(parsed);
            }
        });
        signaling.sendClientMessage(parsed, SignalingMessage.joinRequest(signalingSessionId, bridge.profileId()))
                .whenComplete((u, error) -> { if (error != null) result.completeExceptionally(error); });
        return result;
    }

    public boolean hasIncomingJoinRequest(UUID peerPmid) { return incomingJoinRequests.containsKey(peerPmid); }
    public boolean hasOutgoingJoinRequests()             { return !outgoingJoinRequests.isEmpty(); }

    public OutgoingJoinState outgoingJoinState(UUID peerPmid) {
        OutgoingJoinRequest pending = outgoingJoinRequests.get(peerPmid);
        if (pending != null) {
            return pending.sdpStarted().get() ? OutgoingJoinState.CONNECTING : OutgoingJoinState.AWAITING_HOST_ACCEPT;
        }
        var hs = manager.getHandshake(peerPmid);
        return (hs != null && hs.isInitiator()) ? OutgoingJoinState.CONNECTED : OutgoingJoinState.NONE;
    }

    @Nullable
    public UUID connectingOutgoingJoinPmid() {
        for (Map.Entry<UUID, OutgoingJoinRequest> e : outgoingJoinRequests.entrySet()) {
            if (e.getValue().sdpStarted().get()) return e.getKey();
        }
        return null;
    }

    /** Map view (for the HTTP layer) of pending incoming requests: pmid -> sessionId. */
    public Map<UUID, String> incomingJoinRequestsView() {
        return java.util.Collections.unmodifiableMap(incomingJoinRequests);
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
        var existing = manager.getHandshake(peerPmid);
        if (existing != null && !existing.isInitiator()) existing.abort("join request rejected");
    }

    public CompletableFuture<Void> declineInvite(UUID hostPmid) {
        manager.ensureSignalingConnected();
        return signaling.sendClientMessage(hostPmid, SignalingMessage.inviteDeclined())
                .whenComplete((u, t) -> manager.maybeDisconnectSignaling())
                .exceptionallyCompose(err -> {
                    LOGGER.warn("[P2P][join] Failed to decline invite: {}", err.getMessage());
                    return CompletableFuture.failedFuture(err);
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
        outgoingJoinRequests.forEach((peerPmid, request) -> {
            if (outgoingJoinRequests.remove(peerPmid, request)) {
                request.result().completeExceptionally(new CancellationException("join request cancelled"));
                notifyJoinStateChanged();
            }
        });
    }

    public void shutdown() {
        signaling.removeConnectionListener(connectionListener);
        incomingJoinRequests.clear();
        outgoingJoinRequests.forEach((u, req) -> req.result().completeExceptionally(new CancellationException("shutdown")));
        outgoingJoinRequests.clear();
        acceptedAwaitingOffer.clear();
        notifyJoinStateChanged();
    }

    public void addJoinStateListener(Runnable r)    { joinStateListeners.add(r); }
    public void removeJoinStateListener(Runnable r) { joinStateListeners.remove(r); }

    void notifyJoinStateChanged() {
        bridge.executeOnClientThread(() -> joinStateListeners.forEach(Runnable::run));
    }

    // -------- inbound dispatch --------

    private void handle(UUID fromPmid, SignalingMessage.FriendJoin msg) {
        switch (msg) {
            case SignalingMessage.FriendJoin.Request   r -> handleJoinRequest(fromPmid, r);
            case SignalingMessage.FriendJoin.Accepted  a -> handleJoinAccepted(fromPmid, a.sessionId());
            case SignalingMessage.FriendJoin.Rejected  r -> handleJoinRejected(fromPmid, r.sessionId());
            case SignalingMessage.FriendJoin.InviteDeclined ignored -> handleInviteDeclined(fromPmid);
        }
    }

    private void handleJoinRequest(UUID fromPmid, SignalingMessage.FriendJoin.Request request) {
        String sessionId = request.sessionId();
        LOGGER.info("[P2P][host] handleJoinRequest fromPmid={} sessionId={} isHosting={}", fromPmid, sessionId, manager.isHostingP2P());
        if (!manager.isHostingP2P()) {
            LOGGER.info("[P2P][host] Not hosting P2P, rejecting join request fromPmid={} sessionId={}", fromPmid, sessionId);
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
        LOGGER.info("[P2P][host] join request check: fromPmid={} requestProfileId={} isFriend={} isInvited={} invitedBatch={}",
                fromPmid, requestProfileId, isFriend, isInvited, social.getPresenceHandler().getInvitedPlayersBatch());
        if (!isFriend) {
            LOGGER.info("[P2P][host] Ignoring join request (not a friend) fromPmid={}", fromPmid);
            return;
        }
        if (isInvited) {
            LOGGER.info("[P2P][host] Auto-accepting join request (invited) fromPmid={} sessionId={}", fromPmid, sessionId);
            sendJoinAccepted(fromPmid, sessionId).thenRun(() -> clearHostInvite(fromPmid)).exceptionally(t -> null);
            return;
        }
        LOGGER.info("[P2P][host] Guest not in invite list, queuing as incoming join request fromPmid={} sessionId={}", fromPmid, sessionId);
        incomingJoinRequests.put(fromPmid, sessionId);
        notifyJoinStateChanged();

        UUID peerProfileId = social.getPresenceHandler().getProfileIdFromPmid(fromPmid);
        if (peerProfileId == null) return;
        social.getFriends().stream()
                .filter(p -> p.id().equals(peerProfileId))
                .findAny()
                .ifPresent(friend -> bridge.executeOnClientThread(
                        () -> { if (NotificationPrefs.get().notifyJoinRequest) bridge.notifyToast("friend.join_request", friend.name(), friend.id()); }));
    }

    private void handleJoinAccepted(UUID fromPmid, String sessionId) {
        OutgoingJoinRequest request = outgoingJoinRequests.get(fromPmid);
        if (request == null) {
            LOGGER.debug("[P2P][join] Ignoring join acceptance for session {} (no pending join request)", sessionId);
            return;
        }
        if (!request.sessionId().equals(sessionId)) {
            LOGGER.debug("[P2P][join] Ignoring stale join acceptance for session {} (pending={})", sessionId, request.sessionId());
            return;
        }
        if (!request.sdpStarted().compareAndSet(false, true)) {
            LOGGER.debug("[P2P][join] Ignoring duplicate join acceptance for session {}", sessionId);
            return;
        }
        notifyJoinStateChanged();
        if (manager.hasHandshake(fromPmid)) {
            request.result().completeExceptionally(new IllegalStateException("Handshake already in progress"));
            return;
        }
        manager.startHandshake(fromPmid, sessionId).whenComplete((u, error) -> {
            if (error != null) {
                request.result().completeExceptionally(error);
            } else {
                request.result().complete(null);
                manager.cancelOutgoingJoins();
            }
        });
    }

    private void handleJoinRejected(UUID fromPmid, String sessionId) {
        LOGGER.info("[P2P][join] handleJoinRejected fromPmid={} sessionId={}", fromPmid, sessionId);
        OutgoingJoinRequest request = outgoingJoinRequests.get(fromPmid);
        if (request != null && request.sessionId().equals(sessionId)) {
            LOGGER.info("[P2P][join] Join request rejected by host fromPmid={} sessionId={}", fromPmid, sessionId);
            request.result().completeExceptionally(new RuntimeException("Join request rejected"));
        } else {
            LOGGER.info("[P2P][join] Ignoring join rejection for session {} (no matching pending join request, outgoing={})", sessionId, outgoingJoinRequests.keySet());
        }
    }

    private void handleInviteDeclined(UUID fromPmid) {
        LOGGER.info("[P2P][host] handleInviteDeclined fromPmid={}", fromPmid);
        if (!socialSupplier.get().isFriendsPmid(fromPmid)) {
            LOGGER.info("[P2P][host] Ignoring invite decline (not a friend) fromPmid={}", fromPmid);
            return;
        }
        clearHostInvite(fromPmid);
    }

    private void clearHostInvite(UUID peerPmid) {
        socialSupplier.get().getPresenceHandler().clearInviteForPmid(peerPmid);
    }

    private CompletableFuture<Void> sendJoinAccepted(UUID peerPmid, String sessionId) {
        acceptedAwaitingOffer.put(peerPmid, sessionId);
        return signaling.sendClientMessage(peerPmid, SignalingMessage.joinAccepted(sessionId))
                .exceptionallyCompose(err -> {
                    acceptedAwaitingOffer.remove(peerPmid, sessionId);
                    LOGGER.warn("[P2P][host] Failed to send join acceptance for session {}: {}", sessionId, err.getMessage());
                    return CompletableFuture.failedFuture(err);
                });
    }

    private void showJoinInviteExpiredToast(UUID peerPmid) {
        bridge.executeOnClientThread(() -> {
            UUID peerProfileId = socialSupplier.get().getPresenceHandler().getProfileIdFromPmid(peerPmid);
            if (peerProfileId == null) return;
            socialSupplier.get().getFriends().stream()
                    .filter(p -> p.id().equals(peerProfileId))
                    .findAny()
                    .ifPresent(friend -> { if (NotificationPrefs.get().notifyJoinRequest) bridge.notifyToast("friend.join_expired", friend.name(), friend.id()); });
        });
    }
}
