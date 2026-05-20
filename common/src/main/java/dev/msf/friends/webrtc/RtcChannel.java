package dev.msf.friends.webrtc;

import dev.msf.friends.util.Logging;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import dev.onvoid.webrtc.RTCPeerConnection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Strict 26.2 port of {@code net.minecraft.client.network.webrtc.RtcChannel}.
 *
 * <p>Implementation notes (verbatim from 26.2):
 * <ul>
 *   <li>Outbound writes are split into 256 KiB chunks before being handed to
 *       {@code RTCDataChannel.send}.</li>
 *   <li>When the data-channel's buffered amount reaches 1 MiB the channel is
 *       marked unwritable via {@link ChannelOutboundBuffer#setUserDefinedWritability(int, boolean)}
 *       (flag bit&nbsp;1); it becomes writable again as soon as the buffered
 *       amount drops back below 256 KiB.</li>
 *   <li>The peer connection is kept alive after {@code dispose} runs only on the
 *       handshake side; this class always disposes it on close.</li>
 * </ul>
 */
public final class RtcChannel extends AbstractChannel {
    private static final Logger LOGGER = Logging.get();
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final int MAX_CHUNK_SIZE = 262144;        // 256 KiB
    private static final long HIGH_WATER_MARK = 1048576L;    // 1 MiB
    private static final long LOW_WATER_MARK  = 262144L;     // 256 KiB
    private static final int  BACKPRESSURE_FLAG = 1;

    /** Mirrors the {@code net.minecraft.network.Connection.SECURE_TRANSPORT} attribute key. */
    public static final AttributeKey<Boolean> SECURE_TRANSPORT = AttributeKey.valueOf("secure_transport");

    /** Pre-verified profile ID set during acceptGuest. Server login mixin checks this
     *  to skip Mojang online authentication (identity already confirmed via signaling). */
    public static final AttributeKey<java.util.UUID> INTENDED_PROFILE_ID = AttributeKey.valueOf("msf_intended_profile_id");

    private final RtcHandshake.HandshakeResult handshakeResult;
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private volatile boolean closed;
    private volatile boolean activated;
    private boolean writeStalled;

    public RtcChannel(RtcHandshake.HandshakeResult handshakeResult) {
        super(null);
        this.handshakeResult = handshakeResult;
        attr(SECURE_TRANSPORT).set(Boolean.TRUE);
    }

    @Override public ChannelMetadata metadata() { return METADATA; }
    @Override public ChannelConfig config()    { return config; }
    @Override public boolean isOpen()   { return !closed; }
    @Override public boolean isActive() { return activated && !closed; }

    @Override protected SocketAddress localAddress0()  { return new InetSocketAddress("rtc-local", 0); }
    @Override protected SocketAddress remoteAddress0() { return new InetSocketAddress("rtc-remote", 0); }

    @Override protected boolean isCompatible(EventLoop loop) { return loop instanceof SingleThreadEventLoop; }
    @Override protected AbstractUnsafe newUnsafe() { return new RtcUnsafe(); }

    @Override
    protected void doRegister(ChannelPromise promise) {
        LOGGER.debug("[rtc-channel] doRegister, DataChannel state={}", handshakeResult.dataChannel().getState());
        RTCDataChannelState initial = handshakeResult.dataChannel().getState();
        eventLoop().execute(() -> {
            handleStateChange(initial);
            handshakeResult.dataChannel().registerObserver(new RTCDataChannelObserver() {
                @Override public void onMessage(RTCDataChannelBuffer buffer) {
                    ByteBuf copy = Unpooled.copiedBuffer(buffer.data);
                    eventLoop().execute(() -> handleMessage(copy));
                }
                @Override public void onStateChange() {
                    RTCDataChannelState state = handshakeResult.dataChannel().getState();
                    LOGGER.debug("[rtc-channel] DataChannel state -> {}", state);
                    eventLoop().execute(() -> handleStateChange(state));
                }
                @Override public void onBufferedAmountChange(long previousAmount) {
                    if (handshakeResult.dataChannel().getBufferedAmount() <= LOW_WATER_MARK) {
                        eventLoop().execute(() -> setWriteStalled(false));
                    }
                }
            });
            promise.setSuccess();
        });
    }

    @Override protected void doBind(SocketAddress localAddress) {
        throw new UnsupportedOperationException("RtcChannel cannot be bound");
    }

    @Override protected void doDisconnect() { closeFromTransport(); }

    @Override protected void doClose() {
        if (!closed) {
            closed = true;
            RTCDataChannel dc = handshakeResult.dataChannel();
            RTCPeerConnection pc = handshakeResult.peerConnection();
            // Unregister the observer and initiate a graceful SCTP close on the DataChannel.
            // The PeerConnection is closed with a short delay so any buffered writes
            // (e.g. the Minecraft disconnect packet) have time to be transmitted to the
            // remote peer before the DTLS/ICE transport is torn down.  Without this delay
            // peerConnection.close() aborts the SCTP layer while the disconnect packet is
            // still in-flight, causing the remote peer to see a generic "disconnected"
            // reason instead of the server's actual disconnect message.
            try { dc.unregisterObserver(); } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] unregisterObserver threw", e); }
            try { dc.close();              } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] close threw", e); }
            CompletableFuture.delayedExecutor(500L, TimeUnit.MILLISECONDS).execute(() -> {
                try { dc.dispose();  } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] dispose threw", e); }
                try { pc.close();    } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] peerConnection.close threw", e); }
            });
        }
    }

    @Override protected void doBeginRead() {
        // RTCDataChannel pushes asynchronously; nothing to do.
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        Object msg;
        while ((msg = in.current()) != null) {
            if (msg instanceof ByteBuf buf) writeByteBuf(buf);
            in.remove();
            if (handshakeResult.dataChannel().getBufferedAmount() >= HIGH_WATER_MARK) {
                setWriteStalled(true);
                return;
            }
        }
    }

    private void writeByteBuf(ByteBuf buf) throws Exception {
        int remaining = buf.readableBytes();
        int idx = buf.readerIndex();
        while (remaining > 0) {
            int chunk = Math.min(remaining, MAX_CHUNK_SIZE);
            byte[] bytes = new byte[chunk];
            buf.getBytes(idx, bytes);
            try {
                handshakeResult.dataChannel().send(new RTCDataChannelBuffer(ByteBuffer.wrap(bytes), true));
            } catch (Exception e) {
                LOGGER.error("[P2P-Netty] Failed to send DataChannel message", e);
                throw e;
            }
            idx += chunk;
            remaining -= chunk;
        }
    }

    private void setWriteStalled(boolean stalled) {
        if (closed || stalled == this.writeStalled) return;
        this.writeStalled = stalled;
        ChannelOutboundBuffer outbound = unsafe().outboundBuffer();
        if (outbound != null) outbound.setUserDefinedWritability(BACKPRESSURE_FLAG, !stalled);
        if (!stalled) unsafe().flush();
    }

    private void handleMessage(ByteBuf buf) {
        if (closed || !activated || !config.isAutoRead()) {
            buf.release();
            return;
        }
        pipeline().fireChannelRead(buf);
        pipeline().fireChannelReadComplete();
    }

    private void handleStateChange(RTCDataChannelState state) {
        if (closed) return;
        switch (state) {
            case OPEN -> {
                if (!activated) {
                    LOGGER.info("[rtc-channel] DataChannel OPEN, activating channel");
                    activated = true;
                    pipeline().fireChannelActive();
                }
            }
            case CLOSING, CLOSED -> closeFromTransport();
            default -> {}
        }
    }

    private void closeFromTransport() {
        if (!closed) {
            LOGGER.debug("[rtc-channel] Closing RtcChannel from transport");
            unsafe().close(voidPromise());
        }
    }

    public static void dispose(RtcHandshake.HandshakeResult r) {
        dispose(r.peerConnection(), r.dataChannel());
    }

    public static void dispose(RTCPeerConnection peerConnection, @Nullable RTCDataChannel dataChannel) {
        if (dataChannel != null) {
            try { dataChannel.unregisterObserver(); } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] unregisterObserver threw", e); }
            try { dataChannel.close(); } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] close threw", e); }
            try { dataChannel.dispose(); } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] dispose threw", e); }
        }
        if (peerConnection != null) {
            try { peerConnection.close(); } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] peerConnection.close threw", e); }
        }
    }

    private final class RtcUnsafe extends AbstractUnsafe {
        @Override public void connect(SocketAddress remote, SocketAddress local, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException("RtcChannel is already connected to its RTCDataChannel"));
        }
    }
}
