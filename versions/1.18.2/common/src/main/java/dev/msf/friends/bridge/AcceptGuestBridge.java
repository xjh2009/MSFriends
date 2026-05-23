package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Server-side bridge: accepts an incoming P2P guest connection on the
 * integrated server. Equivalent of 26.2's {@code ServerConnectionListener.acceptChannel()}.
 *
 * <p>Uses a {@link ChannelInitializer} to guarantee that the pipeline is fully
 * configured <em>before</em> the channel becomes active — eliminating the race
 * condition where data arrives before handlers are ready. This mirrors 26.2's
 * atomic {@code acceptChannel} design.
 *
 * <p>1.18.2 adaptations:
 * <ul>
 *   <li>No {@code configureSerialization} — pipeline set up inline with codec classes</li>
 *   <li>Forge init is included for cross-platform compatibility (no-op on Fabric)</li>
 * </ul>
 */
public final class AcceptGuestBridge {
    private static final Logger LOGGER = Logging.get();
    private static final DefaultEventLoopGroup EVENT_LOOP_GROUP = new DefaultEventLoopGroup(1, r -> {
        Thread t = new Thread(r, "msf-rtc-server-netty");
        t.setDaemon(true);
        return t;
    });

    public static void acceptGuest(Channel rtcChannel, UUID guestProfileId) throws Exception {
        LOGGER.info("[accept-guest] accepting guest profileId={}", guestProfileId);

        // --- resolve classes ---
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        Class<?> packetFlowClass = FabricReflect.mcClass("net.minecraft.network.protocol.PacketFlow");
        Class<?> minecraftClass  = FabricReflect.mcClass("net.minecraft.client.Minecraft");
        Class<?> minecraftServerClass = FabricReflect.mcClass("net.minecraft.server.MinecraftServer");
        Class<?> sclClass = FabricReflect.mcClass("net.minecraft.server.network.ServerConnectionListener");
        Class<?> handshakeListenerClass = FabricReflect.mcClass("net.minecraft.server.network.ServerHandshakePacketListenerImpl");
        Class<?> packetListenerClass = FabricReflect.mcClass("net.minecraft.network.PacketListener");

        // --- get IntegratedServer ---
        Object minecraft = FabricReflect.mcMethod(minecraftClass, "getInstance").invoke(null);
        Object server = FabricReflect.mcMethod(minecraftClass, "getSingleplayerServer").invoke(minecraft);
        if (server == null) {
            LOGGER.warn("[accept-guest] no singleplayer server running");
            rtcChannel.close();
            return;
        }

        // --- get ServerConnectionListener + connections list ---
        Field sclField = FabricReflect.mcField(minecraftServerClass, "connection");
        sclField.setAccessible(true);
        Object scl = sclField.get(server);
        if (scl == null) {
            LOGGER.warn("[accept-guest] ServerConnectionListener is null");
            rtcChannel.close();
            return;
        }

        @SuppressWarnings("unchecked")
        List<Object> connections = (List<Object>) FabricReflect.mcMethod(sclClass, "getConnections").invoke(scl);

        // --- set intended profile ID on channel (for ServerLoginMixin pre-auth) ---
        rtcChannel.attr(RtcChannel.INTENDED_PROFILE_ID).set(guestProfileId);

        // --- resolve methods needed inside the initializer ---
        Object serverbound = Enum.valueOf((Class<Enum>) packetFlowClass, "SERVERBOUND");

        // Resolve packet codec classes for inline pipeline setup
        // (1.18.2 does NOT have configureSerialization/configurePacketHandler — pipeline is set up inline)
        Class<?> packetDecoderClass = Class.forName("net.minecraft.network.PacketDecoder");
        Class<?> packetEncoderClass = Class.forName("net.minecraft.network.PacketEncoder");
        Class<?> varintDecoderClass = Class.forName("net.minecraft.network.Varint21FrameDecoder");
        Class<?> varintPrependerClass = Class.forName("net.minecraft.network.Varint21LengthFieldPrepender");
        Object clientbound = Enum.valueOf((Class<Enum>) packetFlowClass, "CLIENTBOUND");

        final Object fClientbound = clientbound;
        Constructor<?> handshakeCtor = findCtor(handshakeListenerClass);
        Method setListener = FabricReflect.mcMethod(connectionClass, "setPacketListener", packetListenerClass);

        // Capture final refs for use inside the ChannelInitializer
        final Object fServer = server;
        final Object fServerbound = serverbound;

        // --- Atomic pipeline setup via ChannelInitializer ---
        // The initializer's initChannel runs BEFORE channelActive fires,
        // guaranteeing that all handlers are in place when the first data arrives.
        // This is the same pattern 26.2's acceptChannel uses.
        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                // Create Connection WITHOUT setting channel field.
                // Connection.channelActive will set it when channel becomes active.
                Object connection = ConnectionFactory.createUnbound(
                        ConnectionFactory.PacketFlowDirection.SERVERBOUND);

                // Standard remote-connection pipeline (1.18.2 inline — no configureSerialization method)
                // This mirrors Connection$1.initChannel from MC 1.18.2
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                pipeline.addLast("splitter", (ChannelHandler) varintDecoderClass.getDeclaredConstructor().newInstance());
                pipeline.addLast("decoder", (ChannelHandler) packetDecoderClass.getDeclaredConstructor(packetFlowClass).newInstance(fServerbound));
                pipeline.addLast("prepender", (ChannelHandler) varintPrependerClass.getDeclaredConstructor().newInstance());
                pipeline.addLast("encoder", (ChannelHandler) packetEncoderClass.getDeclaredConstructor(packetFlowClass).newInstance(fClientbound));
                pipeline.addLast("packet_handler", (ChannelHandler) connection);

                // Set handshake listener — ServerLoginMixin will intercept
                // handleHello and use the pre-set INTENDED_PROFILE_ID.
                Class<?>[] ptypes = handshakeCtor.getParameterTypes();
                Object[] args = new Object[ptypes.length];
                for (int i = 0; i < ptypes.length; i++) {
                    if (ptypes[i] == minecraftServerClass) args[i] = fServer;
                    else if (ptypes[i] == connectionClass) args[i] = connection;
                    else if (ptypes[i] == String.class)    args[i] = "rtc-guest";
                    else if (ptypes[i] == int.class || ptypes[i] == Integer.class) args[i] = 0;
                    else if (ptypes[i] == boolean.class || ptypes[i] == Boolean.class) args[i] = false;
                    else args[i] = null;
                }
                Object handshakeListener = handshakeCtor.newInstance(args);
                setListener.invoke(connection, handshakeListener);

                // Add to server's connections list
                synchronized (connections) {
                    connections.add(connection);
                }

                // --- Forge network initialization ---
                // Connection.channelActive() does: super.channelActive(ctx) → ctx.fireChannelActive()
                // then this.channel = ctx.channel(). So by the time our handler's channelActive
                // fires, Connection.channel is still null. We must defer the Forge init call
                // so it runs AFTER Connection.channelActive() completes.
                pipeline.addLast("forge-init", new ChannelInboundHandlerAdapter() {
                    boolean fired = false;
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        ctx.fireChannelActive();
                        if (!fired) {
                            fired = true;
                            // Schedule after current channelActive propagation completes,
                            // so Connection.channel field is set by then.
                            ctx.executor().execute(() -> {
                                try {
                                    Class<?> networkRegistryClass = Class.forName("net.minecraftforge.network.NetworkRegistry");
                                    networkRegistryClass.getMethod("onConnectionStart", connectionClass)
                                            .invoke(null, connection);
                                    LOGGER.info("[accept-guest] Forge NetworkRegistry.onConnectionStart called successfully");
                                } catch (ClassNotFoundException e) {
                                    // Not Forge — skip
                                } catch (Throwable t) {
                                    LOGGER.warn("[accept-guest] Failed to call Forge NetworkRegistry.onConnectionStart", t);
                                }
                                // Remove self after firing
                                ctx.pipeline().remove(this);
                            });
                        }
                    }
                });

                LOGGER.info("[accept-guest] guest connection accepted (atomic), profileId={}", guestProfileId);
            }
        });

        // --- Register triggers: initChannel → channelRegistered → channelActive ---
        // By the time channelActive fires (and RtcChannel.doRegister detects DataChannel OPEN),
        // the pipeline is already fully configured.
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
    }

    private static Constructor<?> findCtor(Class<?> clazz) throws Exception {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) { if (c.getParameterCount() >= 2) return c; }
        throw new RuntimeException("Cannot find ServerHandshakePacketListenerImpl constructor");
    }
}
