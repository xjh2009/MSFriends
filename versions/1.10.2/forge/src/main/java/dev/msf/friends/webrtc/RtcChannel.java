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
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Java 8 port of net.minecraft.client.network.webrtc.RtcChannel.
 *
 * Outbound writes are split into 256 KiB chunks. When the data-channel
 * buffered amount reaches 1 MiB the channel is marked unwritable via
 * ChannelOutboundBuffer.setUserDefinedWritability (flag bit 1); it
 * becomes writable again as soon as the buffered amount drops below 256 KiB.
 */
public final class RtcChannel extends AbstractChannel {
    private static final Logger LOGGER = Logging.get();
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final int MAX_CHUNK_SIZE = 262144;
    private static final long HIGH_WATER_MARK = 1048576L;
    private static final long LOW_WATER_MARK  = 262144L;
    private static final int  BACKPRESSURE_FLAG = 1;

    public static final AttributeKey<Boolean> SECURE_TRANSPORT = AttributeKey.valueOf("secure_transport");
    public static final AttributeKey<java.util.UUID> INTENDED_PROFILE_ID = AttributeKey.valueOf("msf_intended_profile_id");

    /** Shared scheduler for delayed close operations. */
    private static final ScheduledExecutorService CLOSE_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "RtcChannel-Close");
                t.setDaemon(true);
                return t;
            });

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
    protected void doRegister() throws Exception {
        LOGGER.debug("[rtc-channel] doRegister, DataChannel state={}", handshakeResult.dataChannel().getState());
        final RTCDataChannelState initial = handshakeResult.dataChannel().getState();
        eventLoop().execute(new Runnable() {
            @Override public void run() {
                handleStateChange(initial);
                handshakeResult.dataChannel().registerObserver(new RTCDataChannelObserver() {
                    @Override public void onMessage(RTCDataChannelBuffer buffer) {
                        final ByteBuf copy = Unpooled.copiedBuffer(buffer.data);
                        eventLoop().execute(new Runnable() {
                            @Override public void run() { handleMessage(copy); }
                        });
                    }
                    @Override public void onStateChange() {
                        final RTCDataChannelState state = handshakeResult.dataChannel().getState();
                        LOGGER.debug("[rtc-channel] DataChannel state -> {}", state);
                        eventLoop().execute(new Runnable() {
                            @Override public void run() { handleStateChange(state); }
                        });
                    }
                    @Override public void onBufferedAmountChange(long previousAmount) {
                        if (handshakeResult.dataChannel().getBufferedAmount() <= LOW_WATER_MARK) {
                            eventLoop().execute(new Runnable() {
                                @Override public void run() { setWriteStalled(false); }
                            });
                        }
                    }
                });
                // Netty 4.0: no ChannelPromise param, registration handled by framework
            }
        });
    }

    @Override protected void doBind(SocketAddress localAddress) {
        throw new UnsupportedOperationException("RtcChannel cannot be bound");
    }

    @Override protected void doDisconnect() { closeFromTransport(); }

    @Override protected void doClose() {
        if (!closed) {
            closed = true;
            final RTCDataChannel dc = handshakeResult.dataChannel();
            final RTCPeerConnection pc = handshakeResult.peerConnection();
            try { dc.unregisterObserver(); } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] unregisterObserver threw", e); }
            try { dc.close();              } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] close threw", e); }
            // Delay dispose/close so any buffered writes (disconnect packet) can be transmitted
            CLOSE_SCHEDULER.schedule(new Runnable() {
                @Override public void run() {
                    try { dc.dispose();  } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] dispose threw", e); }
                    try { pc.close();    } catch (RuntimeException e) { LOGGER.warn("[rtc-channel] peerConnection.close threw", e); }
                }
            }, 500L, TimeUnit.MILLISECONDS);
        }
    }

    @Override protected void doBeginRead() {
        // RTCDataChannel pushes asynchronously
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        Object msg;
        while ((msg = in.current()) != null) {
            // Java 8: no pattern matching instanceof
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                writeByteBuf(buf);
            }
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
        if (!stalled) unsafe().flush();
        // Netty 4.0 has no setUserDefinedWritability; the isWritable() override handles it
        pipeline().fireChannelWritabilityChanged();
    }

    @Override
    public boolean isWritable() {
        return super.isWritable() && !writeStalled;
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
        if (state == RTCDataChannelState.OPEN) {
            if (!activated) {
                LOGGER.info("[rtc-channel] DataChannel OPEN, activating channel");
                activated = true;
                pipeline().fireChannelActive();
            }
        } else if (state == RTCDataChannelState.CLOSING || state == RTCDataChannelState.CLOSED) {
            closeFromTransport();
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

    public static void dispose(RTCPeerConnection peerConnection, RTCDataChannel dataChannel) {
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
