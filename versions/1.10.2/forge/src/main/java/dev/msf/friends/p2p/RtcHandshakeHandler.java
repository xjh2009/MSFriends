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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Java 8 port of RtcHandshakeHandler.
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
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RtcHandshake-Timeout");
        t.setDaemon(true);
        return t;
    });
    private PeerConnectionFactory factory;
    private volatile boolean webrtcNativeAvailable = true;

    private final SignalingServiceClient.ConnectionListener connectionListener = new SignalingServiceClient.ConnectionListener() {
        @Override public void onSignalingError(UUID peerPmid, SignalingException cause) {
            if (peerPmid == null) {
                LOGGER.debug("Signaling error: {}", cause.getClass().getSimpleName());
                return;
            }
            RtcHandshake handshake = getHandshake(peerPmid);
            if (handshake != null) handshake.abort("signaling error: " + cause.getClass().getSimpleName());
        }
        @Override public void onSignalingDisconnected() {}
    };

    RtcHandshakeHandler(MinecraftBridge bridge, SignalingServiceClient signaling,
                        P2PManager manager, Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.signaling = signaling;
        this.manager = manager;
        this.socialSupplier = socialSupplier;
        signaling.setWebRtcSignalingHandler(new SignalingServiceClient.WebRtcSignalingHandler() {
            @Override public void handle(UUID fromPmid, SignalingMessage.WebRtc message) {
                handleWebRtc(fromPmid, message);
            }
        });
        signaling.addConnectionListener(connectionListener);
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
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException("WebRTC native library unavailable; P2P is disabled"));
            return f;
        }
        return startHandshake(peerPmid, sessionId, true,
                new Function<RtcHandshake, CompletableFuture<SignalingMessage>>() {
                    @Override public CompletableFuture<SignalingMessage> apply(RtcHandshake handshake) {
                        return handshake.createOffer().thenApply(new Function<String, SignalingMessage>() {
                            @Override public SignalingMessage apply(String sdp) {
                                return SignalingMessage.offer(handshake.id(), sdp);
                            }
                        });
                    }
                });
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
        signaling.removeConnectionListener(connectionListener);
        for (RtcHandshake h : handshakes.values()) h.abort("shutdown");
        handshakes.clear();
        if (factory != null) {
            try { factory.dispose(); } catch (RuntimeException e) { LOGGER.warn("[rtc] factory.dispose threw", e); }
            factory = null;
        }
    }

    private void handleWebRtc(UUID fromPmid, SignalingMessage.WebRtc msg) {
        if (msg instanceof SignalingMessage.WebRtc.Offer) {
            handleOffer(fromPmid, (SignalingMessage.WebRtc.Offer) msg);
        } else if (msg instanceof SignalingMessage.WebRtc.Answer) {
            handleAnswer(fromPmid, (SignalingMessage.WebRtc.Answer) msg);
        } else if (msg instanceof SignalingMessage.WebRtc.IceCandidate) {
            handleIceCandidate(fromPmid, (SignalingMessage.WebRtc.IceCandidate) msg);
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
                new Function<RtcHandshake, CompletableFuture<SignalingMessage>>() {
                    @Override public CompletableFuture<SignalingMessage> apply(final RtcHandshake handshake) {
                        return handshake.acceptOffer(msg.sdp()).thenApply(new Function<String, SignalingMessage>() {
                            @Override public SignalingMessage apply(String sdp) {
                                return SignalingMessage.answer(handshake.id(), sdp);
                            }
                        });
                    }
                }).exceptionally(t -> null);
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
        existing.applyAnswer(msg.sdp()).exceptionally(new Function<Throwable, Void>() {
            @Override public Void apply(Throwable err) {
                existing.abort("answer failed: " + err.getMessage());
                return null;
            }
        });
    }

    private void handleIceCandidate(UUID fromPmid, SignalingMessage.WebRtc.IceCandidate msg) {
        RtcHandshake handshake = getHandshake(fromPmid);
        if (handshake == null) { LOGGER.trace("Dropping ICE candidate for session {} (no handshake)", msg.sessionId()); return; }
        if (!handshake.id().equals(msg.sessionId())) {
            LOGGER.trace("Dropping stale ICE candidate for session {} (current={})", msg.sessionId(), handshake.id());
            return;
        }
        RTCIceCandidate candidate = msg.candidate().toRtcIceCandidate();
        handshake.addRemoteIceCandidate(candidate).exceptionally(new Function<Throwable, Void>() {
            @Override public Void apply(Throwable err) {
                LOGGER.warn("Failed to add remote ICE candidate for session {}: {}", msg.sessionId(), err.getMessage());
                return null;
            }
        });
    }

    private CompletableFuture<Void> startHandshake(UUID peerPmid, String sessionId, boolean initiator,
                                                   Function<RtcHandshake, CompletableFuture<SignalingMessage>> sdpOp) {
        if (handshakes.containsKey(peerPmid)) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException("Handshake already in progress"));
            return f;
        }
        final CompletableFuture<Void> result = new CompletableFuture<>();
        final Instant attemptStart = Instant.now();
        final AtomicReference<Instant> signalingDoneAt = new AtomicReference<>();
        final P2PTelemetryEvent.State telemetry = new P2PTelemetryEvent.State();
        buildIceServers(TurnPrefs.get().turnMode).thenCompose(new Function<List<RTCIceServer>, CompletableFuture<Void>>() {
            @Override public CompletableFuture<Void> apply(List<RTCIceServer> iceServers) {
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
                        .thenCompose(new Function<SignalingMessage, CompletableFuture<Void>>() {
                            @Override public CompletableFuture<Void> apply(SignalingMessage sdpMsg) {
                                return signaling.sendClientMessage(peerPmid, sdpMsg);
                            }
                        })
                        .whenComplete((v, error) -> {
                            if (error != null) {
                                telemetry.setFailureStage(P2PTelemetryEvent.FailureStage.SIGNALING);
                                handshake.abort("SDP exchange failed: " + error.getMessage());
                                result.completeExceptionally(error);
                            } else {
                                signalingDoneAt.set(Instant.now());
                            }
                        });
            }
        }).whenComplete((v, error) -> {
            if (error != null) {
                LOGGER.warn("P2P handshake failed for session {}: {}", sessionId, error.toString());
                telemetry.setFailureStage(P2PTelemetryEvent.FailureStage.SIGNALING);
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private CompletableFuture<List<RTCIceServer>> buildIceServers(TurnPrefs.TurnMode mode) {
        if (mode == TurnPrefs.TurnMode.DISABLED) {
            return signaling.requestTurnAuth()
                    .thenApply(new Function<RTCIceServer, List<RTCIceServer>>() {
                        @Override public List<RTCIceServer> apply(RTCIceServer s) {
                            return s != null ? Collections.singletonList(s) : Collections.<RTCIceServer>emptyList();
                        }
                    })
                    .exceptionally(new Function<Throwable, List<RTCIceServer>>() {
                        @Override public List<RTCIceServer> apply(Throwable e) { return Collections.emptyList(); }
                    });
        } else if (mode == TurnPrefs.TurnMode.REMOVE_MOJANG) {
            return mergilinkTurnClient.fetchTurnServers();
        } else if (mode == TurnPrefs.TurnMode.MOJANG_FIRST) {
            final CompletableFuture<RTCIceServer> mojang = signaling.requestTurnAuth()
                    .exceptionally(new Function<Throwable, RTCIceServer>() {
                        @Override public RTCIceServer apply(Throwable e) { return null; }
                    });
            final CompletableFuture<List<RTCIceServer>> mergi = mergilinkTurnClient.fetchTurnServers();
            return mojang.thenCombine(mergi, (m, ms) -> {
                List<RTCIceServer> list = new ArrayList<>();
                if (m != null) list.add(m);
                list.addAll(ms);
                return list;
            });
        } else {
            // MERGILINK_FIRST
            final CompletableFuture<RTCIceServer> mojang = signaling.requestTurnAuth()
                    .exceptionally(new Function<Throwable, RTCIceServer>() {
                        @Override public RTCIceServer apply(Throwable e) { return null; }
                    });
            final CompletableFuture<List<RTCIceServer>> mergi = mergilinkTurnClient.fetchTurnServers();
            return mergi.thenCombine(mojang, (ms, m) -> {
                List<RTCIceServer> list = new ArrayList<>(ms);
                if (m != null) list.add(m);
                return list;
            });
        }
    }

    private RtcHandshake createHandshake(final UUID peerPmid, final String sessionId, boolean initiator,
                                          List<RTCIceServer> iceServers,
                                          final CompletableFuture<Void> result,
                                          final P2PTelemetryEvent.State telemetry) {
        RTCConfiguration config = new RTCConfiguration();
        config.iceServers.addAll(iceServers);
        RtcHandshake handshake;
        synchronized (this) {
            PeerConnectionFactory pcf = getPeerConnectionFactory();
            if (pcf == null) {
                result.completeExceptionally(
                        new IllegalStateException("WebRTC native library unavailable; P2P is disabled"));
                return null;
            }
            handshake = new RtcHandshake(pcf, config, sessionId, initiator,
                    new java.util.function.Consumer<RTCIceCandidate>() {
                        @Override public void accept(RTCIceCandidate candidate) {
                            signaling.sendClientMessage(peerPmid,
                                    SignalingMessage.iceCandidate(sessionId, candidate))
                                    .exceptionally(new Function<Throwable, Void>() {
                                        @Override public Void apply(Throwable err) {
                                            LOGGER.warn("Failed to send ICE candidate for session {}: {}", sessionId, err.getMessage());
                                            return null;
                                        }
                                    });
                        }
                    });
            if (handshakes.putIfAbsent(peerPmid, handshake) != null) {
                handshake.abort("Duplicate");
                return null;
            }
        }

        timeoutScheduler.schedule(new Runnable() {
            @Override public void run() {
                if (!result.isDone()) {
                    telemetry.setFailureStage(P2PTelemetryEvent.FailureStage.TIMEOUT);
                    handshake.abort("Handshake timeout");
                }
            }
        }, PENDING_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        handshake.onIceInfo(new java.util.function.Consumer<RtcHandshake.IceInfo>() {
            @Override public void accept(RtcHandshake.IceInfo info) {
                telemetry.setIceInfo(info.local(), info.remote());
            }
        });

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
