package dev.msf.friends.webrtc;

import dev.onvoid.webrtc.*;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * RtcChannel - Java 8 compatible version.
 * Wraps a WebRTC data channel for P2P communication.
 */
public class RtcChannel implements AutoCloseable {
    private static final Logger LOGGER = Logging.get();

    private final RtcHandshake.HandshakeResult result;
    private final RTCDataChannel dataChannel;
    private final RTCPeerConnection peerConnection;
    private volatile boolean closed = false;
    private volatile java.util.function.Consumer<byte[]> messageHandler;

    public RtcChannel(RtcHandshake.HandshakeResult result) {
        this.result = result;
        this.dataChannel = result.dataChannel();
        this.peerConnection = result.peerConnection();
        dataChannel.registerObserver(new RTCDataChannelObserver() {
            @Override public void onMessage(RTCDataChannelBuffer buffer) {
                if (messageHandler != null) {
                    byte[] data = new byte[buffer.data.remaining()];
                    buffer.data.get(data);
                    messageHandler.accept(data);
                }
            }
            @Override public void onStateChange() {
                RTCDataChannelState state = dataChannel.getState();
                if (state == RTCDataChannelState.CLOSED) {
                    closed = true;
                }
            }
            @Override public void onBufferedAmountChange(long amount) {}
        });
    }

    public void send(byte[] data) {
        if (closed) return;
        try {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data);
            RTCDataChannelBuffer dcBuffer = new RTCDataChannelBuffer(buffer, true);
            dataChannel.send(dcBuffer);
        } catch (Exception e) {
            LOGGER.warn("[rtc] Failed to send data", e);
        }
    }

    public void onMessage(java.util.function.Consumer<byte[]> handler) {
        this.messageHandler = handler;
    }

    public boolean isOpen() { return !closed && dataChannel.getState() == RTCDataChannelState.OPEN; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { dataChannel.close(); } catch (Exception e) { /* ignore */ }
        try { peerConnection.close(); } catch (Exception e) { /* ignore */ }
    }

    public static void dispose(RtcHandshake.HandshakeResult result) {
        try { result.dataChannel().close(); } catch (Exception e) { /* ignore */ }
        try { result.peerConnection().close(); } catch (Exception e) { /* ignore */ }
    }
}
