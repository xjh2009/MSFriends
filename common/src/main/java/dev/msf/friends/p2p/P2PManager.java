package dev.msf.friends.p2p;

import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.client.SignalingServiceClient;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcHandshake;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Strict 26.2 port of {@code P2PManager}, parameterised over a {@link MinecraftBridge}. */
public final class P2PManager {
    private static final Logger LOGGER = Logging.get();
    private static final long SIGNALING_RECONNECT_DELAY_SECONDS = 1L;

    private final MinecraftBridge bridge;
    private final SignalingServiceClient signaling;
    private final FriendJoinHandler friendJoinHandler;
    private final RtcHandshakeHandler rtcHandshakeHandler;
    private volatile boolean shutdown;

    public P2PManager(MinecraftBridge bridge, SignalingServiceClient.UserCredentials user,
                      Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.signaling = new SignalingServiceClient(user);
        this.friendJoinHandler = new FriendJoinHandler(bridge, signaling, this, socialSupplier);
        this.rtcHandshakeHandler = new RtcHandshakeHandler(bridge, signaling, this, socialSupplier);
        signaling.addConnectionListener(new SignalingServiceClient.ConnectionListener() {
            @Override public void onSignalingDisconnected() { onSignalingDisconnected(); }
        });
    }

    private void onSignalingDisconnected() {
        if (shutdown || !needsSignaling()) return;
        LOGGER.warn("Signaling disconnected while still needed, scheduling reconnect");
        CompletableFuture.delayedExecutor(SIGNALING_RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!shutdown && needsSignaling()) ensureSignalingConnected();
        });
    }

    private boolean needsSignaling() {
        return isHostingP2P() || friendJoinHandler.hasOutgoingJoinRequests();
    }

    public boolean isHostingP2P() { return bridge.isHostingP2P(); }

    public void ensureSignalingConnected() { signaling.connect(); }

    public void maybeDisconnectSignaling() {
        if (!needsSignaling()) signaling.disconnect();
    }

    public void onHostScopeChanged(MinecraftBridge.MultiplayerScope scope) {
        LOGGER.info("Host scope changed to {}", scope);
        if (isHostingP2P()) ensureSignalingConnected();
        else teardownHostState();
    }

    public void onHostServerStopping() { teardownHostState(); }

    private void teardownHostState() {
        rtcHandshakeHandler.closeHostHandshakes();
        friendJoinHandler.clearHostJoinRequestState();
        maybeDisconnectSignaling();
    }

    public CompletableFuture<Void> joinPlayer(String peerPmid) {
        return friendJoinHandler.joinPlayer(peerPmid);
    }

    public void cancelOutgoingJoins() {
        friendJoinHandler.cancelOutgoingJoins();
        rtcHandshakeHandler.cancelInitiatorHandshakes();
        maybeDisconnectSignaling();
    }

    public boolean hasIncomingJoinRequest(UUID peerPmid)  { return friendJoinHandler.hasIncomingJoinRequest(peerPmid); }
    public boolean hasOutgoingJoinRequest()               { return friendJoinHandler.hasOutgoingJoinRequests(); }
    public FriendJoinHandler.OutgoingJoinState outgoingJoinState(UUID peerPmid) {
        return friendJoinHandler.outgoingJoinState(peerPmid);
    }

    @Nullable
    public UUID connectingOutgoingJoinPmid() { return friendJoinHandler.connectingOutgoingJoinPmid(); }

    public void acceptIncomingJoinRequest(UUID peerPmid) { friendJoinHandler.acceptIncomingJoinRequest(peerPmid); }
    public void rejectIncomingJoinRequest(UUID peerPmid) { friendJoinHandler.rejectIncomingJoinRequest(peerPmid); }
    public CompletableFuture<Void> declineInvite(UUID hostPmid) { return friendJoinHandler.declineInvite(hostPmid); }

    public void addJoinStateListener(Runnable r)    { friendJoinHandler.addJoinStateListener(r); }
    public void removeJoinStateListener(Runnable r) { friendJoinHandler.removeJoinStateListener(r); }
    public void notifyJoinStateChanged()             { friendJoinHandler.notifyJoinStateChanged(); }

    public synchronized void shutdown() {
        shutdown = true;
        signaling.clearHandlers();
        rtcHandshakeHandler.shutdown();
        friendJoinHandler.shutdown();
        signaling.disconnect();
    }

    public boolean hasHandshake(UUID peerPmid) { return rtcHandshakeHandler.hasHandshake(peerPmid); }

    @Nullable
    public RtcHandshake getHandshake(UUID peerPmid) { return rtcHandshakeHandler.getHandshake(peerPmid); }

    public CompletableFuture<Void> startHandshake(UUID peerPmid, String sessionId) {
        return rtcHandshakeHandler.startHandshake(peerPmid, sessionId);
    }

    public boolean consumeAcceptedJoinRequest(UUID peerPmid, String sessionId) {
        return friendJoinHandler.consumeAcceptedJoinRequest(peerPmid, sessionId);
    }

    /** Public access for the HTTP layer. */
    public FriendJoinHandler friendJoinHandler() { return friendJoinHandler; }
    public SignalingServiceClient signaling()    { return signaling; }
}
