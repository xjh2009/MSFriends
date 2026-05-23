package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import io.netty.channel.Channel;
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
 * Server-side bridge for MC 1.19.2: accepts an incoming P2P guest connection
 * on the integrated server.
 *
 * <p>1.19.2 differences from 1.21.11:
 * <ul>
 *   <li>No BandwidthDebugMonitor</li>
 *   <li>Different configureSerialization signature (3 params, not 4)</li>
 *   <li>Different ServerHandshakePacketListenerImpl constructor</li>
 *   <li>setListenerForServerboundHandshake may not exist; use setListener</li>
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

        // --- set intended profile ID on channel ---
        rtcChannel.attr(RtcChannel.INTENDED_PROFILE_ID).set(guestProfileId);

        // --- resolve methods ---
        Object serverbound = Enum.valueOf((Class<Enum>) packetFlowClass, "SERVERBOUND");

        // 1.19.2: configureSerialization has no BandwidthDebugMonitor param
        final Method configureSerialization = resolveMethod(connectionClass,
                "configureSerialization", "setupSerialization",
                ChannelPipeline.class, packetFlowClass, boolean.class);

        Constructor<?> handshakeCtor = handshakeListenerClass.getConstructors()[0];

        // Try setListenerForServerboundHandshake, fallback to setListener
        final Method setListener = resolveMethod(connectionClass,
                "setListenerForServerboundHandshake", "setListener",
                packetListenerClass);

        // Capture finales
        final Object fServer = server;
        final Object fServerbound = serverbound;

        // --- ChannelInitializer: atomic pipeline setup ---
        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                Object connection = ConnectionFactory.createUnbound(
                        ConnectionFactory.PacketFlowDirection.SERVERBOUND);

                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                configureSerialization.invoke(null, pipeline, fServerbound, false);

                // Configure packet handler
                try {
                    Method configurePacketHandler = FabricReflect.mcMethod(connectionClass, "configurePacketHandler", ChannelPipeline.class);
                    configurePacketHandler.invoke(connection, pipeline);
                } catch (NoSuchMethodException e) {
                    // 1.19.2 may use different method name
                    LOGGER.debug("[accept-guest] no configurePacketHandler, pipeline may already be configured");
                }

                // Set handshake listener
                Object handshakeListener = handshakeCtor.newInstance(fServer, connection);
                setListener.invoke(connection, handshakeListener);

                // Add to server's connections list
                synchronized (connections) {
                    connections.add(connection);
                }

                LOGGER.info("[accept-guest] guest connection accepted, profileId={}", guestProfileId);
            }
        });

        // --- Register ---
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
    }

    /** Try the primary method name, fall back to the alternate if not found. */
    private static Method resolveMethod(Class<?> owner, String primary, String fallback, Class<?>... paramTypes) throws NoSuchMethodException {
        try {
            return FabricReflect.mcMethod(owner, primary, paramTypes);
        } catch (NoSuchMethodException e) {
            return FabricReflect.mcMethod(owner, fallback, paramTypes);
        }
    }
}
