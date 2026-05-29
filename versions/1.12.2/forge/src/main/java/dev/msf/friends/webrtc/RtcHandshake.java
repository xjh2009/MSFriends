package dev.msf.friends.webrtc;

import dev.onvoid.webrtc.*;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * RtcHandshake - Java 8 compatible version.
 */
public class RtcHandshake {
    private static final Logger LOGGER = Logging.get();

    private final String sessionId;
    private final boolean initiator;
    private final RTCPeerConnection peerConnection;
    private final CompletableFuture<HandshakeResult> future = new CompletableFuture<>();
    private final IceCandidateSender iceCandidateSender;
    private final ExecutorService executor;

    public interface IceCandidateSender {
        void send(RTCIceCandidate candidate);
    }

    /** BiConsumer<HandshakeResult, Throwable> that is also compatible with whenComplete. */
    public interface BiVoidAction extends java.util.function.BiConsumer<HandshakeResult, Throwable> {
    }

    public static class IceInfo {
        private final String local;
        private final String remote;
        public IceInfo(String local, String remote) { this.local = local; this.remote = remote; }
    }

    public static class HandshakeResult {
        private final RTCPeerConnection peerConnection;
        private final RTCDataChannel dataChannel;

        public HandshakeResult(RTCPeerConnection pc, RTCDataChannel dc) {
            this.peerConnection = pc; this.dataChannel = dc;
        }
        public RTCPeerConnection peerConnection() { return peerConnection; }
        public RTCDataChannel dataChannel() { return dataChannel; }
    }

    public RtcHandshake(PeerConnectionFactory factory, RTCConfiguration config,
                        String sessionId, boolean initiator, IceCandidateSender sender) {
        this.sessionId = sessionId;
        this.initiator = initiator;
        this.iceCandidateSender = sender;
        this.executor = Executors.newSingleThreadExecutor();
        this.peerConnection = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override public void onIceCandidate(RTCIceCandidate c) { iceCandidateSender.send(c); }
            @Override public void onIceConnectionChange(RTCIceConnectionState s) {}
            @Override public void onDataChannel(RTCDataChannel dc) { onDataChannelReady(dc); }
            @Override public void onSignalingChange(RTCSignalingState s) {}
            @Override public void onIceGatheringChange(RTCIceGatheringState s) {}
            @Override public void onRenegotiationNeeded() {}
        });
    }

    public String id() { return sessionId; }
    public boolean isInitiator() { return initiator; }

    public CompletableFuture<HandshakeResult> future() { return future; }

    public CompletableFuture<String> createOffer() {
        final CompletableFuture<String> result = new CompletableFuture<>();
        RTCOfferOptions options = new RTCOfferOptions();
        peerConnection.createOffer(options, new CreateSessionDescriptionObserver() {
            @Override public void onSuccess(RTCSessionDescription desc) {
                peerConnection.setLocalDescription(desc, new SetSessionDescriptionObserver() {
                    @Override public void onSuccess() { result.complete(desc.sdp); }
                    @Override public void onFailure(String error) { result.completeExceptionally(new RuntimeException(error)); }
                });
            }
            @Override public void onFailure(String error) { result.completeExceptionally(new RuntimeException(error)); }
        });
        return result;
    }

    public CompletableFuture<Void> acceptOffer(String sdp) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        RTCSessionDescription offer = new RTCSessionDescription(RTCSdpType.OFFER, sdp);
        peerConnection.setRemoteDescription(offer, new SetSessionDescriptionObserver() {
            @Override public void onSuccess() {
                RTCAnswerOptions options = new RTCAnswerOptions();
                peerConnection.createAnswer(options, new CreateSessionDescriptionObserver() {
                    @Override public void onSuccess(RTCSessionDescription desc) {
                        peerConnection.setLocalDescription(desc, new SetSessionDescriptionObserver() {
                            @Override public void onSuccess() { result.complete(null); }
                            @Override public void onFailure(String error) { result.completeExceptionally(new RuntimeException(error)); }
                        });
                    }
                    @Override public void onFailure(String error) { result.completeExceptionally(new RuntimeException(error)); }
                });
            }
            @Override public void onFailure(String error) { result.completeExceptionally(new RuntimeException(error)); }
        });
        return result;
    }

    public CompletableFuture<Void> applyAnswer(String sdp) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        RTCSessionDescription answer = new RTCSessionDescription(RTCSdpType.ANSWER, sdp);
        peerConnection.setRemoteDescription(answer, new SetSessionDescriptionObserver() {
            @Override public void onSuccess() { result.complete(null); }
            @Override public void onFailure(String error) { result.completeExceptionally(new RuntimeException(error)); }
        });
        return result;
    }

    public CompletableFuture<Void> addRemoteIceCandidate(RTCIceCandidate candidate) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        peerConnection.addIceCandidate(candidate);
        result.complete(null);
        return result;
    }

    public void onIceInfo(Consumer<IceInfo> callback) {
        // Stub - would need ICE connection stats
    }

    public void abort(String reason) {
        LOGGER.info("[rtc] Handshake {} aborted: {}", sessionId, reason);
        if (!future.isDone()) future.completeExceptionally(new RuntimeException(reason));
        try { peerConnection.close(); } catch (Exception e) { /* ignore */ }
    }

    private void onDataChannelReady(RTCDataChannel dc) {
        if (!future.isDone()) {
            future.complete(new HandshakeResult(peerConnection, dc));
        }
    }
}
