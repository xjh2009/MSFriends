package dev.msf.friends.p2p;

import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.client.MergilinkTurnClient;
import dev.msf.friends.p2p.client.SignalingServiceClient;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.telemetry.P2PTelemetryEvent;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.TurnPrefs;
import dev.msf.friends.webrtc.RtcChannel;
import dev.msf.friends.webrtc.RtcHandshake;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * RtcHandshakeHandler - Java 8 compatible version.
 */
final class RtcHandshakeHandler {
    private static final Logger LOGGER = Logging.get();
    private static final long PENDING_HANDSHAKE_TIMEOUT_SECONDS = 10L;

    private final MinecraftBridge bridge;
    private final SignalingServiceClient signaling;
    private final P2PManager manager;
    private final Supplier<PlayerSocialManager> socialSupplier;
    private final ConcurrentHashMap<UUID, RtcHandshake> handshakes = new ConcurrentHashMap<>();
    private final MergilinkTurnClient mergilinkTurnClient = new MergilinkTurnClient();
    private final ScheduledExecutorService scheduler;
    private volatile PeerConnectionFactory factory;
    private volatile boolean webrtcNativeAvailable = true;

    RtcHandshakeHandler(MinecraftBridge bridge, SignalingServiceClient signaling,
                        P2PManager manager, Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.signaling = signaling;
        this.manager = manager;
        this.socialSupplier = socialSupplier;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "RTC-Handshake");
                t.setDaemon(true);
                return t;
            }
        });
    }

    private synchronized PeerConnectionFactory getPeerConnectionFactory() {
        if (!webrtcNativeAvailable) return null;
        if (factory == null) {
            try {
                factory = new PeerConnectionFactory();
            } catch (UnsatisfiedLinkError e) {
                webrtcNativeAvailable = false;
                LOGGER.warn("[rtc] WebRTC native library unavailable: {}", e.getMessage());
                return null;
            }
        }
        return factory;
    }

    boolean hasHandshake(UUID peerPmid) { return handshakes.containsKey(peerPmid); }
    RtcHandshake getHandshake(UUID peerPmid) { return handshakes.get(peerPmid); }

    CompletableFuture<Void> startHandshake(UUID peerPmid, String sessionId) {
        if (!webrtcNativeAvailable) {
            return failedFuture(new IllegalStateException("WebRTC native library unavailable"));
        }
        return startHandshakeInternal(peerPmid, sessionId, true);
    }

    void closeHostHandshakes() {
        for (RtcHandshake h : handshakes.values()) {
            if (!h.isInitiator()) h.abort("Host stopped");
        }
    }

    void cancelInitiatorHandshakes() {
        for (RtcHandshake h : handshakes.values()) {
            if (h.isInitiator()) h.abort("handshake cancelled");
        }
    }

    synchronized void shutdown() {
        for (RtcHandshake h : handshakes.values()) h.abort("shutdown");
        handshakes.clear();
        if (factory != null) {
            try { factory.dispose(); } catch (RuntimeException e) { LOGGER.warn("[rtc] factory.dispose threw", e); }
            factory = null;
        }
        scheduler.shutdownNow();
    }

    private CompletableFuture<Void> startHandshakeInternal(UUID peerPmid, String sessionId, boolean initiator) {
        if (handshakes.containsKey(peerPmid)) {
            return failedFuture(new IllegalStateException("Handshake already in progress"));
        }
        final CompletableFuture<Void> result = new CompletableFuture<>();
        final P2PTelemetryEvent.State telemetry = new P2PTelemetryEvent.State();
        final PeerConnectionFactory pcf = getPeerConnectionFactory();
        if (pcf == null) {
            result.completeExceptionally(new IllegalStateException("WebRTC native library unavailable"));
            return result;
        }

        List<RTCIceServer> iceServers = new ArrayList<>();
        try {
            RTCConfiguration config = new RTCConfiguration();
            config.iceServers.addAll(iceServers);
            final RtcHandshake handshake = new RtcHandshake(pcf, config, sessionId, initiator,
                    new RtcHandshake.IceCandidateSender() {
                        @Override public void send(RTCIceCandidate candidate) {
                            signaling.sendClientMessage(peerPmid, SignalingMessage.iceCandidate(sessionId, candidate))
                                    .exceptionally(t -> null);
                        }
                    });
            if (handshakes.putIfAbsent(peerPmid, handshake) != null) {
                handshake.abort("Duplicate");
                result.completeExceptionally(new IllegalStateException("Duplicate handshake"));
                return result;
            }
            scheduler.schedule(new Runnable() {
                @Override public void run() {
                    if (!result.isDone()) {
                        handshake.abort("Handshake timeout");
                        result.completeExceptionally(new TimeoutException("Handshake timeout"));
                    }
                }
            }, PENDING_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            handshake.future().whenComplete(new RtcHandshake.BiVoidAction() {
                @Override public void accept(RtcHandshake.HandshakeResult hr, Throwable err) {
                    handshakes.remove(peerPmid, handshake);
                    manager.notifyJoinStateChanged();
                    if (err != null) {
                        result.completeExceptionally(err);
                        return;
                    }
                    if (!result.complete(null)) {
                        RtcChannel.dispose(hr);
                        return;
                    }
                    try {
                        RtcChannel ch = new RtcChannel(hr);
                        if (initiator) bridge.joinHost(ch, hr.peerConnection());
                        else {
                            UUID profileId = socialSupplier.get().getPresenceHandler().getProfileIdFromPmid(peerPmid);
                            if (profileId != null) bridge.acceptGuest(ch, profileId);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[rtc] bridge call failed", e);
                        RtcChannel.dispose(hr);
                    }
                }
            });

            if (initiator) {
                handshake.createOffer().thenAccept(new java.util.function.Consumer<String>() {
                    @Override public void accept(String sdp) {
                        signaling.sendClientMessage(peerPmid, SignalingMessage.offer(sessionId, sdp))
                                .exceptionally(t -> { handshake.abort("offer send failed"); return null; });
                    }
                });
            }
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable t) {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(t);
        return f;
    }
}
