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
 * Server-side bridge: accepts an incoming P2P guest connection on the
 * integrated server. Equivalent of 26.2's {@code ServerConnectionListener.acceptChannel()}.
 * Adapted for MC 1.20.1 Yarn names.
 *
 * <p>Uses a {@link ChannelInitializer} to guarantee that the pipeline is fully
 * configured <em>before</em> the channel becomes active — eliminating the race
 * condition where data arrives before handlers are ready. This mirrors 26.2's
 * atomic {@code acceptChannel} design.
 *
 * <p>1.20.1 differences:
 * <ul>
 *   <li>No BandwidthDebugMonitor — addHandlers uses 2-arg signature</li>
 *   <li>ClientConnection → Yarn name; NetworkSide → Yarn name</li>
 *   <li>ServerNetworkIo instead of ServerConnectionListener</li>
 *   <li>setPacketListener instead of setListenerForServerboundHandshake</li>
 *   <li>No Forge network initialization (Fabric-only)</li>
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
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.ClientConnection");
        Class<?> networkSideClass = FabricReflect.mcClass("net.minecraft.network.NetworkSide");
        Class<?> minecraftClientClass = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
        Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> serverNetworkIoClass = FabricReflect.mcClass("net.minecraft.server.ServerNetworkIo");
        Class<?> handshakeListenerClass = FabricReflect.mcClass("net.minecraft.server.network.ServerHandshakeNetworkHandler");

        // --- get IntegratedServer ---
        Object minecraft = FabricReflect.mcMethod(minecraftClientClass, "getInstance").invoke(null);
        Object server = FabricReflect.mcMethod(minecraftClientClass, "getServer").invoke(minecraft);
        if (server == null) {
            LOGGER.warn("[accept-guest] no singleplayer server running");
            rtcChannel.close();
            return;
        }

        // --- get ServerNetworkIo + connections list ---
        Field networkIoField = FabricReflect.mcField(minecraftServerClass, "networkIo");
        networkIoField.setAccessible(true);
        Object networkIo = networkIoField.get(server);
        if (networkIo == null) {
            LOGGER.warn("[accept-guest] ServerNetworkIo is null");
            rtcChannel.close();
            return;
        }

        @SuppressWarnings("unchecked")
        List<Object> connections = (List<Object>) FabricReflect.mcMethod(serverNetworkIoClass, "getConnections").invoke(networkIo);

        // --- set intended profile ID on channel (for ServerLoginMixin pre-auth) ---
        rtcChannel.attr(RtcChannel.INTENDED_PROFILE_ID).set(guestProfileId);

        // --- resolve methods needed inside the initializer ---
        Object serverbound = Enum.valueOf((Class<Enum>) networkSideClass, "SERVERBOUND");

        // 1.20.1: ClientConnection.addHandlers(ChannelPipeline, NetworkSide) — 2-arg, no BandwidthDebugMonitor
        Method addHandlers = FabricReflect.mcMethod(connectionClass, "addHandlers",
                ChannelPipeline.class, networkSideClass);

        Constructor<?> handshakeCtor = handshakeListenerClass.getConstructor(minecraftServerClass, connectionClass);

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

                // Standard remote-connection pipeline
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                // 1.20.1: 2-arg addHandlers, no BandwidthDebugMonitor
                addHandlers.invoke(null, pipeline, fServerbound);

                // Set handshake listener — ServerLoginMixin will intercept
                // handleHello and use the pre-set INTENDED_PROFILE_ID.
                Object handshakeListener = handshakeCtor.newInstance(fServer, connection);
                FabricReflect.mcMethod(connectionClass, "setPacketListener",
                        FabricReflect.mcClass("net.minecraft.network.PacketListener"))
                        .invoke(connection, handshakeListener);

                // Add to server's connections list
                synchronized (connections) {
                    connections.add(connection);
                }

                // No Forge network initialization — Fabric-only 1.20.1

                LOGGER.info("[accept-guest] guest connection accepted (atomic), profileId={}", guestProfileId);
            }
        });

        // --- Register triggers: initChannel → channelRegistered → channelActive ---
        // By the time channelActive fires (and RtcChannel.doRegister detects DataChannel OPEN),
        // the pipeline is already fully configured.
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
    }
}
