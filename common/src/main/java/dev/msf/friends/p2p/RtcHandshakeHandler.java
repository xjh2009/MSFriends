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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Strict 26.2 port of {@code RtcHandshakeHandler}, with the
 * {@code Minecraft.execute / IntegratedServer / Connection.fromChannel} parts
 * delegated to {@link MinecraftBridge#joinHost} / {@link MinecraftBridge#acceptGuest}.
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
    @Nullable private PeerConnectionFactory factory;
    /** Set to false permanently once a UnsatisfiedLinkError is observed (e.g. ViaFabricPlus native conflict). */
    private volatile boolean webrtcNativeAvailable = true;

    private final SignalingServiceClient.ConnectionListener connectionListener = new SignalingServiceClient.ConnectionListener() {
        @Override public void onSignalingError(@Nullable UUID peerPmid, SignalingException cause) {
            if (peerPmid == null) {
                LOGGER.debug("Signaling error: {}", cause.getClass().getSimpleName());
                return;
            }
            RtcHandshake handshake = getHandshake(peerPmid);
            if (handshake != null) handshake.abort("signaling error: " + cause.getClass().getSimpleName());
        }
    };

    RtcHandshakeHandler(MinecraftBridge bridge, SignalingServiceClient signaling,
                        P2PManager manager, Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.signaling = signaling;
        this.manager = manager;
        this.socialSupplier = socialSupplier;
        signaling.setWebRtcSignalingHandler(this::handleWebRtc);
        signaling.addConnectionListener(connectionListener);
    }

    @Nullable
    private synchronized PeerConnectionFactory getPeerConnectionFactory() {
        if (!webrtcNativeAvailable) return null;
        if (factory == null) {
            try {
                factory = new PeerConnectionFactory();
            } catch (UnsatisfiedLinkError e) {
                webrtcNativeAvailable = false;
                LOGGER.warn("[rtc] WebRTC native library is incompatible with another mod on the classpath "
                        + "(likely ViaFabricPlus shipping dev.kastle.webrtc). "
                        + "P2P will be disabled for this session. Cause: {}", e.getMessage());
                return null;
            }
        }
        return factory;
    }

    boolean hasHandshake(UUID peerPmid) { return handshakes.containsKey(peerPmid); }

    @Nullable
    RtcHandshake getHandshake(UUID peerPmid) { return handshakes.get(peerPmid); }

    CompletableFuture<Void> startHandshake(UUID peerPmid, String sessionId) {
        if (!webrtcNativeAvailable) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("WebRTC native library unavailable; P2P is disabled"));
        }
        return startHandshake(peerPmid, sessionId, true,
                handshake -> handshake.createOffer().thenApply(sdp -> SignalingMessage.offer(handshake.id(), sdp)));
    }

    void closeHostHandshakes() {
        handshakes.values().forEach(h -> { if (!h.isInitiator()) h.abort("Host stopped"); });
    }

    void cancelInitiatorHandshakes() {
        handshakes.values().forEach(h -> { if (h.isInitiator()) h.abort("handshake cancelled"); });
    }

    synchronized void shutdown() {
        signaling.removeConnectionListener(connectionListener);
        handshakes.values().forEach(h -> h.abort("shutdown"));
        handshakes.clear();
        if (factory != null) {
            try { factory.dispose(); } catch (RuntimeException e) { LOGGER.warn("[rtc] factory.dispose threw", e); }
            factory = null;
        }
    }

    // -------- inbound dispatch --------

    private void handleWebRtc(UUID fromPmid, SignalingMessage.WebRtc msg) {
        if (msg instanceof SignalingMessage.WebRtc.Offer o) {
            handleOffer(fromPmid, o);
        } else if (msg instanceof SignalingMessage.WebRtc.Answer a) {
            handleAnswer(fromPmid, a);
        } else if (msg instanceof SignalingMessage.WebRtc.IceCandidate ic) {
            handleIceCandidate(fromPmid, ic);
        }
    }

    private void handleOffer(UUID fromPmid, SignalingMessage.WebRtc.Offer msg) {
        if (!manager.isHostingP2P()) { LOGGER.debug("Ignoring offer (not hosting)"); return; }
        if (!socialSupplier.get().isFriendsPmid(fromPmid)) { LOGGER.debug("Ignoring offer (not a friend)"); return; }
        if (!manager.consumeAcceptedJoinRequest(fromPmid, msg.sessionId())) {
            LOGGER.debug("Ignoring offer for session {} (join request was not accepted)", msg.sessionId());
            return;
        }
        startHandshake(fromPmid, msg.sessionId(), false,
                handshake -> handshake.acceptOffer(msg.sdp()).thenApply(sdp -> SignalingMessage.answer(handshake.id(), sdp)))
                .exceptionally(t -> null);
    }

    private void handleAnswer(UUID fromPmid, SignalingMessage.WebRtc.Answer msg) {
        RtcHandshake existing = getHandshake(fromPmid);
        if (existing == null || !existing.isInitiator()) {
            LOGGER.debug("Ignoring answer for session {} (no initiator handshake)", msg.sessionId());
            return;
        }
        if (!existing.id().equals(msg.sessionId())) {
            LOGGER.debug("Ignoring stale answer for session {} (current={})", msg.sessionId(), existing.id());
            return;
        }
        existing.applyAnswer(msg.sdp()).exceptionally(err -> { existing.abort("answer failed: " + err.getMessage()); return null; });
    }

    private void handleIceCandidate(UUID fromPmid, SignalingMessage.WebRtc.IceCandidate msg) {
        RtcHandshake handshake = getHandshake(fromPmid);
        if (handshake == null) { LOGGER.trace("Dropping ICE candidate for session {} (no handshake)", msg.sessionId()); return; }
        if (!handshake.id().equals(msg.sessionId())) {
            LOGGER.trace("Dropping stale ICE candidate for session {} (current={})", msg.sessionId(), handshake.id());
            return;
        }
        RTCIceCandidate candidate = msg.candidate().toRtcIceCandidate();
        handshake.addRemoteIceCandidate(candidate)
                .exceptionally(err -> { LOGGER.warn("Failed to add remote ICE candidate for session {}: {}", msg.sessionId(), err.getMessage()); return null; });
    }

    // -------- shared handshake plumbing --------

    private CompletableFuture<Void> startHandshake(UUID peerPmid, String sessionId, boolean initiator,
                                                   Function<RtcHandshake, CompletableFuture<SignalingMessage>> sdpOp) {
        if (handshakes.containsKey(peerPmid)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Handshake already in progress"));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        Instant attemptStart = Instant.now();
        AtomicReference<Instant> signalingDoneAt = new AtomicReference<>();
        P2PTelemetryEvent.State telemetry = new P2PTelemetryEvent.State();
        buildIceServers(TurnPrefs.get().turnMode).thenCompose(iceServers -> {
            RtcHandshake handshake = createHandshake(peerPmid, sessionId, initiator, iceServers, result, telemetry);
            if (handshake == null) {
                result.completeExceptionally(new IllegalStateException("Failed to establish P2P handshake"));
                return CompletableFuture.completedFuture(null);
            }
            if (initiator) {
                result.whenComplete((u, error) ->
                        P2PTelemetryEvent.INSTANCE.send(error == null, telemetry, attemptStart, signalingDoneAt.get(), Instant.now()));
            }
            return sdpOp.apply(handshake)
                    .thenCompose(sdpMsg -> signaling.sendClientMessage(peerPmid, sdpMsg))
                    .whenComplete((v, error) -> {
                        if (error != null) {
                            telemetry.setFailureStage(P2PTelemetryEvent.FailureStage.SIGNALING);
                            handshake.abort("SDP exchange failed: " + error.getMessage());
                            result.completeExceptionally(error);
                        } else {
                            signalingDoneAt.set(Instant.now());
                        }
                    });
        }).whenComplete((v, error) -> {
            if (error != null) {
                LOGGER.warn("P2P handshake failed for session {}: {}", sessionId, error.toString());
                telemetry.setFailureStage(P2PTelemetryEvent.FailureStage.SIGNALING);
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    /**
     * Builds the ICE server list according to the current {@link TurnPrefs.TurnMode}.
     *
     * <ul>
     *   <li>DISABLED — only Mojang TURN (original behaviour)</li>
     *   <li>MOJANG_FIRST — Mojang first, Mergilink appended</li>
     *   <li>MERGILINK_FIRST — Mergilink first, Mojang appended</li>
     *   <li>REMOVE_MOJANG — only Mergilink, Mojang request skipped</li>
     * </ul>
     */
    private CompletableFuture<List<RTCIceServer>> buildIceServers(TurnPrefs.TurnMode mode) {
        return switch (mode) {
            case DISABLED -> signaling.requestTurnAuth()
                    .thenApply(s -> s != null ? List.of(s) : List.<RTCIceServer>of())
                    .exceptionally(e -> List.of());
            case REMOVE_MOJANG -> mergilinkTurnClient.fetchTurnServers();
            case MOJANG_FIRST -> {
                CompletableFuture<RTCIceServer> mojang = signaling.requestTurnAuth()
                        .exceptionally(e -> null);
                CompletableFuture<List<RTCIceServer>> mergi = mergilinkTurnClient.fetchTurnServers();
                yield mojang.thenCombine(mergi, (m, ms) -> {
                    List<RTCIceServer> list = new ArrayList<>();
                    if (m != null) list.add(m);
                    list.addAll(ms);
                    return list;
                });
            }
            case MERGILINK_FIRST -> {
                CompletableFuture<RTCIceServer> mojang = signaling.requestTurnAuth()
                        .exceptionally(e -> null);
                CompletableFuture<List<RTCIceServer>> mergi = mergilinkTurnClient.fetchTurnServers();
                yield mergi.thenCombine(mojang, (ms, m) -> {
                    List<RTCIceServer> list = new ArrayList<>(ms);
                    if (m != null) list.add(m);
                    return list;
                });
            }
        };
    }

    @Nullable
    private RtcHandshake createHandshake(UUID peerPmid, String sessionId, boolean initiator,
                                          List<RTCIceServer> iceServers,
                                          CompletableFuture<Void> result,
                                          P2PTelemetryEvent.State telemetry) {
        RTCConfiguration config = new RTCConfiguration();
        config.iceServers.addAll(iceServers);
        // 26.2 calls portAllocatorConfig.setDisableTcp(true).setEnableIpv6(true).setEnableIpv6OnWifi(true).
        // The webrtc-java 0.14 builder is mutable but the chained setters return PortAllocatorConfig, so we
        // do nothing extra here; the defaults already mirror the desired behaviour for hosted RTC.
        RtcHandshake handshake;
        synchronized (this) {
            PeerConnectionFactory pcf = getPeerConnectionFactory();
            if (pcf == null) {
                result.completeExceptionally(
                        new IllegalStateException("WebRTC native library unavailable; P2P is disabled"));
                return null;
            }
            handshake = new RtcHandshake(pcf, config, sessionId, initiator,
                    candidate -> signaling.sendClientMessage(peerPmid,
                            SignalingMessage.iceCandidate(sessionId, candidate))
                            .exceptionally(err -> { LOGGER.warn("Failed to send ICE candidate for session {}: {}", sessionId, err.getMessage()); return null; }));
            if (handshakes.putIfAbsent(peerPmid, handshake) != null) {
                handshake.abort("Duplicate");
                return null;
            }
        }

        CompletableFuture.delayedExecutor(PENDING_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!result.isDone()) {
                telemetry.setFailureStage(P2PTelemetryEvent.FailureStage.TIMEOUT);
                handshake.abort("Handshake timeout");
            }
        });
        handshake.onIceInfo(info -> telemetry.setIceInfo(info.local(), info.remote()));
        handshake.future().whenComplete((handshakeResult, err) -> {
            handshakes.remove(peerPmid, handshake);
            manager.notifyJoinStateChanged();
            if (err != null) {
                if (!result.isDone()) {
                    telemetry.setFailureStage(P2PTelemetryEvent.FailureStage.ICE_CONNECT);
                    result.completeExceptionally(err);
                }
                return;
            }
            if (!result.complete(null)) {
                RtcChannel.dispose(handshakeResult);
                return;
            }
            if (handshake.isInitiator()) {
                joinHost(handshakeResult);
            } else {
                UUID profileId = socialSupplier.get().getPresenceHandler().getProfileIdFromPmid(peerPmid);
                if (profileId == null) { handshake.abort("No profile ID for peer"); return; }
                acceptGuest(handshakeResult, profileId);
            }
        });
        return handshake;
    }

    private void joinHost(RtcHandshake.HandshakeResult result) {
        try {
            RtcChannel ch = new RtcChannel(result);
            bridge.joinHost(ch, result.peerConnection());
        } catch (RuntimeException e) {
            LOGGER.warn("[rtc] bridge.joinHost not implemented", e);
            RtcChannel.dispose(result);
        }
    }

    private void acceptGuest(RtcHandshake.HandshakeResult result, UUID guestProfileId) {
        try {
            RtcChannel ch = new RtcChannel(result);
            bridge.acceptGuest(ch, guestProfileId);
        } catch (RuntimeException e) {
            LOGGER.warn("[rtc] bridge.acceptGuest not implemented", e);
            RtcChannel.dispose(result);
        }
    }
}
