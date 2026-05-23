package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Client-side bridge: wires an RtcChannel into MC's Connection system
 * and initiates the login handshake to a remote P2P host.
 * Adapted for MC 1.20.1 Yarn names.
 *
 * <p>Differences from 26.1.2:
 * <ul>
 *   <li>No BandwidthDebugMonitor (doesn't exist in 1.20.1)</li>
 *   <li>Uses addHandlers(ChannelPipeline, NetworkSide) instead of configureSerialization(4-arg)</li>
 *   <li>1.20.1 has no disconnectWithProgressScreen; uses disconnect() or disconnect(Screen) instead</li>
 *   <li>ClientLoginNetworkHandler constructor takes (Connection, MinecraftClient, ServerInfo, Screen)</li>
 *   <li>No Forge network initialization (Fabric-only)</li>
 *   <li>Uses setPacketListener instead of initiateServerboundPlayConnection</li>
 * </ul>
 *
 * <p>Functional logic is otherwise identical to 26.1.2.
 */
public final class ConnectionBridge {
    private static final Logger LOGGER = Logging.get();
    private static final DefaultEventLoopGroup EVENT_LOOP_GROUP = new DefaultEventLoopGroup(1, r -> {
        Thread t = new Thread(r, "msf-rtc-netty");
        t.setDaemon(true);
        return t;
    });

    public static void joinHost(Channel rtcChannel, String playerName, UUID profileId) throws Exception {
        LOGGER.info("[connection-bridge] joinHost starting for player={} uuid={}", playerName, profileId);

        // --- resolve classes ---
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.ClientConnection");
        Class<?> networkSideClass = FabricReflect.mcClass("net.minecraft.network.NetworkSide");
        Class<?> minecraftClientClass = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
        Class<?> clientHandshakeClass = FabricReflect.mcClass("net.minecraft.client.network.ClientLoginNetworkHandler");
        Class<?> serverInfoClass = FabricReflect.mcClass("net.minecraft.client.network.ServerInfo");
        Class<?> serverInfoTypeClass = FabricReflect.mcClass("net.minecraft.client.network.ServerInfo$ServerType");
        Class<?> helloPacketClass = FabricReflect.mcClass("net.minecraft.network.packet.login.LoginHelloC2SPacket");
        Class<?> packetClass = FabricReflect.mcClass("net.minecraft.network.packet.Packet");
        Class<?> packetListenerClass = FabricReflect.mcClass("net.minecraft.network.PacketListener");

        // --- get Minecraft instance ---
        Object minecraft = FabricReflect.mcMethod(minecraftClientClass, "getInstance").invoke(null);

        // --- disconnect current world if any (mirrors 26.2 RtcHandshakeHandler.joinHost) ---
        try {
            Field levelField = FabricReflect.mcField(minecraftClientClass, "world");
            levelField.setAccessible(true);
            Object server = FabricReflect.mcMethod(minecraftClientClass, "getServer").invoke(minecraft);
            if (levelField.get(minecraft) != null || server != null) {
                // 1.20.1: no disconnectWithProgressScreen; use disconnect() or disconnect(Screen)
                try {
                    minecraftClientClass.getMethod("disconnect").invoke(minecraft);
                } catch (NoSuchMethodException e) {
                    minecraftClientClass.getMethod("disconnect", FabricReflect.mcClass("net.minecraft.client.gui.screen.Screen"))
                            .invoke(minecraft, (Object) null);
                }
            }
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            LOGGER.debug("[connection-bridge] no level/disconnect: {}", e.getMessage());
        }

        // --- prepare all objects needed inside the ChannelInitializer ---
        Object clientbound = Enum.valueOf((Class<Enum>) networkSideClass, "CLIENTBOUND");

        // 1.20.1: 2-arg addHandlers (no BandwidthDebugMonitor parameter)
        Method addHandlers = FabricReflect.mcMethod(connectionClass, "addHandlers",
                ChannelPipeline.class, networkSideClass);
        Method send = FabricReflect.mcMethod(connectionClass, "send", packetClass);

        // 1.20.1: ServerInfo$ServerType.LAN exists
        Object serverInfoTypeLAN = Enum.valueOf((Class<Enum>) serverInfoTypeClass, "LAN");
        Constructor<?> serverInfoCtor = serverInfoClass.getConstructor(String.class, String.class, serverInfoTypeClass);
        Object serverInfo = serverInfoCtor.newInstance("Online", "rtc-peer", serverInfoTypeLAN);

        Constructor<?> handshakeCtor = clientHandshakeClass.getConstructors()[0];
        // 1.20.1: LoginHelloC2SPacket(String, Optional<PrivateKey>, UUID)
        Constructor<?> helloCtor = helloPacketClass.getConstructor(String.class, java.util.Optional.class, UUID.class);

        Field connectionField = FabricReflect.mcField(minecraftClientClass, "integratedServerConnection");
        connectionField.setAccessible(true);

        // Capture finals
        final Object fMinecraft = minecraft;

        // --- ChannelInitializer: configure everything BEFORE channelActive ---
        // This is the key fix: Connection + pipeline must be ready before
        // channelActive fires, otherwise runOnceConnected's callback never executes
        // (because channelActive already happened and was missed).
        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                // Create Connection WITHOUT setting channel field.
                // Connection.channelActive will set it when the channel becomes active.
                // This ensures isConnected() returns false during initChannel,
                // so runOnceConnected callbacks queue instead of executing immediately.
                Object connection = ConnectionFactory.createUnbound(
                        ConnectionFactory.PacketFlowDirection.CLIENTBOUND);

                // Configure pipeline — 1.20.1: 2-arg addHandlers (no BandwidthDebugMonitor)
                ChannelPipeline pipeline = ch.pipeline();
                addHandlers.invoke(null, pipeline, clientbound);

                // Create login listener — 1.20.1: (Connection, MinecraftClient, ServerInfo, Screen)
                Object handshakeListener = handshakeCtor.newInstance(
                        connection, fMinecraft, serverInfo,
                        null           // parent screen
                );

                // Set the connection listener (1.20.1: setPacketListener, no initiateServerboundPlayConnection)
                FabricReflect.mcMethod(connectionClass, "setPacketListener", packetListenerClass)
                        .invoke(connection, handshakeListener);

                // Queue hello packet send (1.20.1 uses Optional<PrivateKey> for the key)
                Object helloPacket = helloCtor.newInstance(playerName, java.util.Optional.empty(), profileId);
                send.invoke(connection, helloPacket);

                // Set pending connection field
                connectionField.set(fMinecraft, connection);

                // No Forge network initialization needed — Fabric-only 1.20.1

                LOGGER.info("[connection-bridge] initChannel complete, pipeline ready");
            }
        });

        // --- Register: triggers initChannel → channelRegistered → channelActive ---
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();

        LOGGER.info("[connection-bridge] joinHost complete — connection established");
    }
}
