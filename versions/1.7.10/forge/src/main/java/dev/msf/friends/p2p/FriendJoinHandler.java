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

/**
 * Join request lifecycle (1.7.10 / Java 8 port).
 * Replaces record with class, CompletableFuture.delayedExecutor with
 * ScheduledExecutorService, instanceof pattern matching with instanceof+cast,
 * exceptionallyCompose with helper.
 */
public final class FriendJoinHandler {
    private static final Logger LOGGER = Logging.get(FriendJoinHandler.class);
    public static final long JOIN_REQUEST_TIMEOUT_MINUTES = 1L;
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FriendJoin-Timeout");
        t.setDaemon(true);
        return t;
    });

    public enum OutgoingJoinState { NONE, AWAITING_HOST_ACCEPT, CONNECTING, CONNECTED }

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

    private final SignalingServiceClient.ConnectionListener connectionListener = new SignalingServiceClient.ConnectionListener() {
        @Override public void onSignalingError(UUID peerPmid, SignalingException cause) {
            if (peerPmid == null) return;
            OutgoingJoinRequest pending = outgoingJoinRequests.get(peerPmid);
            if (pending != null) pending.result.completeExceptionally(cause);
        }
    };

    public FriendJoinHandler(MinecraftBridge bridge, SignalingServiceClient signaling,
                             P2PManager manager, Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.signaling = signaling;
        this.manager = manager;
        this.socialSupplier = socialSupplier;
        signaling.setFriendJoinHandler(new SignalingServiceClient.FriendJoinConsumer() {
            @Override public void accept(UUID fromPmid, SignalingMessage.FriendJoin msg) {
                handle(fromPmid, msg);
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
            return failedFuture(e);
        }

        OutgoingJoinRequest existing = outgoingJoinRequests.get(parsed);
        if (existing != null) return existing.result;
        if (manager.hasHandshake(parsed)) return CompletableFuture.completedFuture(null);

        String signalingSessionId = UUID.randomUUID().toString();
        final CompletableFuture<Void> result = new CompletableFuture<Void>();
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
        TIMEOUT_EXECUTOR.schedule(new Runnable() {
            @Override public void run() {
                if (!request.sdpStarted.get()
                        && result.completeExceptionally(new TimeoutException("Join request timed out"))) {
                    showJoinInviteExpiredToast(parsed);
                }
            }
        }, JOIN_REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        signaling.sendClientMessage(parsed, SignalingMessage.joinRequest(signalingSessionId, bridge.profileId()))
                .whenComplete(new java.util.function.BiConsumer<Void, Throwable>() {
                    @Override public void accept(Void u, Throwable error) { if (error != null) result.completeExceptionally(error); }
                });
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
            sendJoinAccepted(peerPmid, sessionId).exceptionally(new java.util.function.Function<Throwable, Void>() {
                @Override public Void apply(Throwable t) { return null; }
            });
        } else {
            signaling.sendClientMessage(peerPmid, SignalingMessage.joinRejected(sessionId))
                    .exceptionally(new java.util.function.Function<Throwable, Void>() {
                        @Override public Void apply(Throwable err) {
                            LOGGER.warn("[P2P][host] Failed to reject pending join request: {}", err.getMessage());
                            return null;
                        }
                    });
        }
    }

    public void rejectIncomingJoinRequest(UUID peerPmid) {
        String sessionId = incomingJoinRequests.remove(peerPmid);
        if (sessionId != null) {
            acceptedAwaitingOffer.remove(peerPmid, sessionId);
            signaling.sendClientMessage(peerPmid, SignalingMessage.joinRejected(sessionId))
                    .exceptionally(new java.util.function.Function<Throwable, Void>() {
                        @Override public Void apply(Throwable err) {
                            LOGGER.warn("[P2P][host] Failed to send join rejection: {}", err.getMessage());
                            return null;
                        }
                    });
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
            UUID peerPmid = entry.getKey();
            OutgoingJoinRequest request = entry.getValue();
            if (outgoingJoinRequests.remove(peerPmid, request)) {
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
            @Override public void run() { for (Runnable r : joinStateListeners) r.run(); }
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
            LOGGER.info("[P2P][host] Not hosting P2P, rejecting join request fromPmid={} sessionId={}", fromPmid, sessionId);
            signaling.sendClientMessage(fromPmid, SignalingMessage.joinRejected(sessionId))
                    .exceptionally(new java.util.function.Function<Throwable, Void>() {
                        @Override public Void apply(Throwable err) {
                            LOGGER.warn("[P2P][host] Failed to reject join request: {}", err.getMessage());
                            return null;
                        }
                    });
            return;
        }
        PlayerSocialManager social = socialSupplier.get();
        UUID requestProfileId = request.profileId();
        if (requestProfileId != null) {
            social.getPresenceHandler().rememberPmidMapping(requestProfileId, fromPmid);
        }
        boolean isFriend = social.isFriendsPmid(fromPmid);
        boolean isInvited = social.getPresenceHandler().isInvitedPmid(fromPmid);
        LOGGER.info("[P2P][host] join request check: fromPmid={} requestProfileId={} isFriend={} isInvited={}",
                fromPmid, requestProfileId, isFriend, isInvited);
        if (!isFriend) {
            LOGGER.info("[P2P][host] Ignoring join request (not a friend) fromPmid={}", fromPmid);
            return;
        }
        if (isInvited) {
            LOGGER.info("[P2P][host] Auto-accepting join request (invited) fromPmid={} sessionId={}", fromPmid, sessionId);
            sendJoinAccepted(fromPmid, sessionId).thenRun(new Runnable() {
                @Override public void run() { clearHostInvite(fromPmid); }
            }).exceptionally(new java.util.function.Function<Throwable, Void>() {
                @Override public Void apply(Throwable t) { return null; }
            });
            return;
        }
        LOGGER.info("[P2P][host] Queuing as incoming join request fromPmid={} sessionId={}", fromPmid, sessionId);
        incomingJoinRequests.put(fromPmid, sessionId);
        notifyJoinStateChanged();

        UUID peerProfileId = social.getPresenceHandler().getProfileIdFromPmid(fromPmid);
        if (peerProfileId == null) return;
        final UUID pid = peerProfileId;
        for (PlayerSocialManager.PlayerData friend : social.getFriends()) {
            if (friend.id().equals(pid)) {
                final String friendName = friend.name();
                final UUID friendId = friend.id();
                bridge.executeOnClientThread(new Runnable() {
                    @Override public void run() {
                        if (NotificationPrefs.get().notifyJoinRequest) {
                            bridge.notifyToast("friend.join_request", friendName, friendId);
                        }
                    }
                });
                break;
            }
        }
    }

    private void handleJoinAccepted(UUID fromPmid, String sessionId) {
        OutgoingJoinRequest request = outgoingJoinRequests.get(fromPmid);
        if (request == null) {
            LOGGER.debug("[P2P][join] Ignoring join acceptance for session {} (no pending join request)", sessionId);
            return;
        }
        if (!request.sessionId.equals(sessionId)) {
            LOGGER.debug("[P2P][join] Ignoring stale join acceptance for session {} (pending={})", sessionId, request.sessionId);
            return;
        }
        if (!request.sdpStarted.compareAndSet(false, true)) {
            LOGGER.debug("[P2P][join] Ignoring duplicate join acceptance for session {}", sessionId);
            return;
        }
        notifyJoinStateChanged();
        if (manager.hasHandshake(fromPmid)) {
            request.result.completeExceptionally(new IllegalStateException("Handshake already in progress"));
            return;
        }
        manager.startHandshake(fromPmid, sessionId).whenComplete(new java.util.function.BiConsumer<Void, Throwable>() {
            @Override public void accept(Void u, Throwable error) {
                if (error != null) {
                    request.result.completeExceptionally(error);
                } else {
                    request.result.complete(null);
                    manager.cancelOutgoingJoins();
                }
            }
        });
    }

    private void handleJoinRejected(UUID fromPmid, String sessionId) {
        LOGGER.info("[P2P][join] handleJoinRejected fromPmid={} sessionId={}", fromPmid, sessionId);
        OutgoingJoinRequest request = outgoingJoinRequests.get(fromPmid);
        if (request != null && request.sessionId.equals(sessionId)) {
            LOGGER.info("[P2P][join] Join request rejected by host fromPmid={}", fromPmid);
            request.result.completeExceptionally(new RuntimeException("Join request rejected"));
        } else {
            LOGGER.info("[P2P][join] Ignoring join rejection for session {}", sessionId);
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
        return signaling.sendClientMessage(peerPmid, SignalingMessage.joinAccepted(sessionId));
    }

    private void showJoinInviteExpiredToast(UUID peerPmid) {
        bridge.executeOnClientThread(new Runnable() {
            @Override public void run() {
                UUID peerProfileId = socialSupplier.get().getPresenceHandler().getProfileIdFromPmid(peerPmid);
                if (peerProfileId == null) return;
                final UUID pid = peerProfileId;
                for (PlayerSocialManager.PlayerData friend : socialSupplier.get().getFriends()) {
                    if (friend.id().equals(pid)) {
                        if (NotificationPrefs.get().notifyJoinRequest) {
                            bridge.notifyToast("friend.join_expired", friend.name(), friend.id());
                        }
                        break;
                    }
                }
            }
        });
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable t) {
        CompletableFuture<T> f = new CompletableFuture<T>();
        f.completeExceptionally(t);
        return f;
    }
}
