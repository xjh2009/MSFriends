package dev.msf.friends.webrtc;

import dev.msf.friends.telemetry.P2PTelemetryEvent;
import dev.msf.friends.util.Logging;
import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelInit;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceConnectionState;
import dev.onvoid.webrtc.RTCIceGatheringState;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionIceErrorEvent;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCPriorityType;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.RTCSignalingState;
import dev.onvoid.webrtc.RTCStats;
import dev.onvoid.webrtc.RTCStatsType;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Java 8 port of {@code net.minecraft.client.network.webrtc.RtcHandshake}.
 *
 * The 4-arg constructor wires a fresh RTCPeerConnection; only the
 * initiator side creates a DataChannel (in createOffer()).
 * The responder receives a remote DataChannel via onDataChannel after
 * accepting the offer.
 */
public final class RtcHandshake {
    private static final Logger LOGGER = Logging.get();

    public static final class HandshakeResult {
        private final RTCPeerConnection peerConnection;
        private final RTCDataChannel dataChannel;
        public HandshakeResult(RTCPeerConnection peerConnection, RTCDataChannel dataChannel) {
            this.peerConnection = peerConnection;
            this.dataChannel = dataChannel;
        }
        public RTCPeerConnection peerConnection() { return peerConnection; }
        public RTCDataChannel dataChannel() { return dataChannel; }
    }

    public static final class IceInfo {
        private final P2PTelemetryEvent.IceCandidateType local;
        private final P2PTelemetryEvent.IceCandidateType remote;
        public IceInfo(P2PTelemetryEvent.IceCandidateType local, P2PTelemetryEvent.IceCandidateType remote) {
            this.local = local;
            this.remote = remote;
        }
        public P2PTelemetryEvent.IceCandidateType local() { return local; }
        public P2PTelemetryEvent.IceCandidateType remote() { return remote; }
    }

    private final String id;
    private final RTCPeerConnection peerConnection;
    private final boolean trickleIce = true;
    private final boolean initiator;
    private final Consumer<RTCIceCandidate> onLocalCandidate;
    private final CompletableFuture<HandshakeResult> result = new CompletableFuture<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean handedOff = new AtomicBoolean();
    private volatile CompletableFuture<String> sdpResult;
    private volatile RTCDataChannel dataChannel;
    private volatile Consumer<IceInfo> onIceInfo;

    public RtcHandshake(PeerConnectionFactory factory,
                        RTCConfiguration configuration,
                        String id,
                        boolean initiator,
                        Consumer<RTCIceCandidate> onLocalCandidate) {
        this.id = id;
        this.initiator = initiator;
        this.onLocalCandidate = onLocalCandidate;
        this.peerConnection = factory.createPeerConnection(configuration, new SessionObserver());
    }

    public String id() { return id; }
    public boolean isInitiator() { return initiator; }
    public CompletableFuture<HandshakeResult> future() { return result; }
    public void onIceInfo(Consumer<IceInfo> cb) { this.onIceInfo = cb; }

    public void abort(String reason) { failHandshake(reason); }

    private void markOpen(RTCDataChannel dc) {
        if (result.isDone()) return;
        handedOff.set(true);
        if (!result.complete(new HandshakeResult(peerConnection, dc))) {
            handedOff.set(false);
        } else {
            try { dc.unregisterObserver(); }
            catch (RuntimeException e) { LOGGER.warn("[P2P][{}] dataChannel.unregisterObserver at handoff threw: {}", id, e.getMessage()); }
            LOGGER.info("[P2P][{}] handshake complete; peerConnection + dataChannel handed off", id);
        }
    }

    private void failHandshake(String reason) {
        Throwable failure = new CancellationException("Handshake " + id + " aborted: " + reason);
        if (!result.completeExceptionally(failure)) return;
        LOGGER.info("[P2P][{}] handshake aborted: {}", id, reason);
        CompletableFuture<String> pending = sdpResult;
        if (pending != null) pending.completeExceptionally(failure);
        if (!handedOff.get()) RtcChannel.dispose(peerConnection, dataChannel);
    }

    private void wireDataChannel(RTCDataChannel dc) {
        this.dataChannel = dc;
        dc.registerObserver(new RTCDataChannelObserver() {
            @Override public void onStateChange() {
                RTCDataChannelState state = dc.getState();
                LOGGER.info("[P2P][{}] DataChannel -> {}", id, state);
                if (state == RTCDataChannelState.OPEN) {
                    markOpen(dc);
                } else if (state == RTCDataChannelState.CLOSING || state == RTCDataChannelState.CLOSED) {
                    failHandshake("Data channel " + state);
                }
            }
            @Override public void onMessage(RTCDataChannelBuffer buf) {}
            @Override public void onBufferedAmountChange(long prev) {}
        });
        if (dc.getState() == RTCDataChannelState.OPEN) markOpen(dc);
    }

    public CompletableFuture<Void> applyAnswer(String answerSdp) {
        if (result.isDone()) return CompletableFuture.completedFuture(null);
        RTCSignalingState signalingState;
        try { signalingState = peerConnection.getSignalingState(); }
        catch (RuntimeException e) {
            if (result.isDone()) return CompletableFuture.completedFuture(null);
            CompletableFuture<Void> f = new CompletableFuture<>(); f.completeExceptionally(e); return f;
        }
        if (signalingState == RTCSignalingState.STABLE) {
            LOGGER.debug("[P2P][{}] ignoring duplicate answer", id);
            return CompletableFuture.completedFuture(null);
        }
        return setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, answerSdp));
    }

    public CompletableFuture<Void> addRemoteIceCandidate(RTCIceCandidate candidate) {
        if (result.isDone()) return CompletableFuture.completedFuture(null);
        try { peerConnection.addIceCandidate(candidate); return CompletableFuture.completedFuture(null); }
        catch (RuntimeException e) { CompletableFuture<Void> f = new CompletableFuture<>(); f.completeExceptionally(e); return f; }
    }

    public CompletableFuture<String> createOffer() {
        if (!started.compareAndSet(false, true)) {
            CompletableFuture<String> f = new CompletableFuture<>(); f.completeExceptionally(new IllegalStateException("Cannot create offer after handshake has started")); return f;
        }
        RTCDataChannelInit init = new RTCDataChannelInit();
        init.ordered = true;
        init.maxRetransmits = -1;
        init.priority = RTCPriorityType.HIGH;
        wireDataChannel(peerConnection.createDataChannel("minecraft", init));
        return startSdpExchange(createOfferSdp().thenCompose(this::setLocalDescription));
    }

    public CompletableFuture<String> acceptOffer(String offerSdp) {
        if (!started.compareAndSet(false, true)) {
            CompletableFuture<String> f = new CompletableFuture<>(); f.completeExceptionally(new IllegalStateException("Cannot accept offer after handshake has started")); return f;
        }
        return startSdpExchange(
                setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, offerSdp))
                        .thenCompose(unused -> createAnswerSdp())
                        .thenCompose(this::setLocalDescription));
    }

    private CompletableFuture<Void> setRemoteDescription(RTCSessionDescription desc) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        peerConnection.setRemoteDescription(desc, new SetSessionDescriptionObserver() {
            @Override public void onSuccess() { f.complete(null); }
            @Override public void onFailure(String err) { f.completeExceptionally(new RuntimeException("setRemoteDescription: " + err)); }
        });
        return f;
    }

    private CompletableFuture<Void> setLocalDescription(RTCSessionDescription desc) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        peerConnection.setLocalDescription(desc, new SetSessionDescriptionObserver() {
            @Override public void onSuccess() { f.complete(null); }
            @Override public void onFailure(String err) { f.completeExceptionally(new RuntimeException("setLocalDescription: " + err)); }
        });
        return f;
    }

    private CompletableFuture<RTCSessionDescription> createOfferSdp() {
        CompletableFuture<RTCSessionDescription> f = new CompletableFuture<>();
        peerConnection.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            @Override public void onSuccess(RTCSessionDescription desc) { f.complete(desc); }
            @Override public void onFailure(String err) { f.completeExceptionally(new RuntimeException("createOffer: " + err)); }
        });
        return f;
    }

    private CompletableFuture<RTCSessionDescription> createAnswerSdp() {
        CompletableFuture<RTCSessionDescription> f = new CompletableFuture<>();
        peerConnection.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
            @Override public void onSuccess(RTCSessionDescription desc) { f.complete(desc); }
            @Override public void onFailure(String err) { f.completeExceptionally(new RuntimeException("createAnswer: " + err)); }
        });
        return f;
    }

    private CompletableFuture<String> startSdpExchange(CompletableFuture<Void> pipeline) {
        CompletableFuture<String> sdpFuture = new CompletableFuture<>();
        this.sdpResult = sdpFuture;
        pipeline.whenComplete((v, err) -> {
            if (err != null) sdpFuture.completeExceptionally(err);
            else if (trickleIce) completeSdp(sdpFuture);
        });
        return sdpFuture.whenComplete((v, err) -> {
            this.sdpResult = null;
            if (err != null) LOGGER.warn("[P2P][{}] SDP exchange failed: {}", id, err.getMessage());
        });
    }

    private void completeSdp(CompletableFuture<String> sdp) {
        RTCSessionDescription local = peerConnection.getLocalDescription();
        if (local == null) {
            sdp.completeExceptionally(new IllegalStateException("local description missing after setLocalDescription"));
        } else {
            sdp.complete(local.sdp);
        }
    }

    private void fireLocalCandidate(RTCIceCandidate candidate) {
        try { onLocalCandidate.accept(candidate); }
        catch (RuntimeException e) { LOGGER.warn("[P2P][{}] onLocalCandidate threw", id, e); }
    }

    private void fireIceInfo(IceInfo info) {
        if (result.isDone()) return;
        Consumer<IceInfo> cb = this.onIceInfo;
        if (cb == null) return;
        try { cb.accept(info); }
        catch (RuntimeException e) { LOGGER.warn("[P2P][{}] onIceInfo threw", id, e); }
    }

    private void reportIceInfo() {
        peerConnection.getStats(report -> {
            if (result.isDone()) return;
            Map<String, RTCStats> all = report.getStats();
            // Java 8: no Optional.isEmpty()
            Optional<RTCStats> nominatedPair = all.values().stream()
                    .filter(s -> s.getType() == RTCStatsType.CANDIDATE_PAIR
                            && Boolean.TRUE.equals(s.getAttributes().get("nominated")))
                    .findFirst();
            if (!nominatedPair.isPresent()) {
                LOGGER.debug("[P2P][{}] selected ICE pair missing from stats", id);
                return;
            }
            Optional<IceInfo> info = extractIceInfo(nominatedPair.get(), all);
            if (!info.isPresent()) {
                LOGGER.debug("[P2P][{}] selected ICE pair missing candidate details", id);
                return;
            }
            LOGGER.info("[P2P][{}] selected ICE pair: {}/{}", id, info.get().local(), info.get().remote());
            fireIceInfo(info.get());
        });
    }

    private Optional<IceInfo> extractIceInfo(RTCStats pair, Map<String, RTCStats> all) {
        Map<String, Object> attrs = pair.getAttributes();
        RTCStats local  = all.get(String.valueOf(attrs.get("localCandidateId")));
        RTCStats remote = all.get(String.valueOf(attrs.get("remoteCandidateId")));
        if (local == null || remote == null) return Optional.empty();
        Object lt = local.getAttributes().get("candidateType");
        Object rt = remote.getAttributes().get("candidateType");
        if (lt == null || rt == null) return Optional.empty();
        Optional<P2PTelemetryEvent.IceCandidateType> lcc = P2PTelemetryEvent.IceCandidateType.byName(String.valueOf(lt));
        Optional<P2PTelemetryEvent.IceCandidateType> rcc = P2PTelemetryEvent.IceCandidateType.byName(String.valueOf(rt));
        if (!lcc.isPresent() || !rcc.isPresent()) {
            LOGGER.debug("[P2P][{}] unknown ICE candidate type local={} remote={}", id, lt, rt);
            return Optional.empty();
        }
        return Optional.of(new IceInfo(lcc.get(), rcc.get()));
    }

    private final class SessionObserver implements PeerConnectionObserver {
        @Override public void onSignalingChange(RTCSignalingState s) { LOGGER.debug("[P2P][{}] signaling -> {}", id, s); }
        @Override public void onIceConnectionChange(RTCIceConnectionState s) { LOGGER.debug("[P2P][{}] ICE -> {}", id, s); }

        @Override public void onIceCandidate(RTCIceCandidate c) {
            if (!result.isDone() && trickleIce) fireLocalCandidate(c);
        }

        @Override public void onIceCandidateError(RTCPeerConnectionIceErrorEvent e) {
            LOGGER.warn("[P2P][{}] ICE error: url={} code={} text={}", id, e.getUrl(), e.getErrorCode(), e.getErrorText());
        }

        @Override public void onConnectionChange(RTCPeerConnectionState state) {
            LOGGER.info("[P2P][{}] connection -> {}", id, state);
            if (state == RTCPeerConnectionState.CONNECTED) {
                reportIceInfo();
            } else if (state == RTCPeerConnectionState.FAILED) {
                failHandshake("connection FAILED");
            } else if (state == RTCPeerConnectionState.CLOSED) {
                failHandshake("connection CLOSED");
            }
        }

        @Override public void onIceGatheringChange(RTCIceGatheringState state) {
            if (!trickleIce && state == RTCIceGatheringState.COMPLETE) {
                CompletableFuture<String> pending = sdpResult;
                if (pending != null) completeSdp(pending);
            }
        }

        @Override public void onDataChannel(RTCDataChannel dc) {
            LOGGER.info("[P2P][{}] DataChannel received (state={})", id, dc.getState());
            wireDataChannel(dc);
        }
    }
}
