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
 * Client-side bridge: wires an RtcChannel into MC's ClientConnection system
 * and initiates the login handshake to a remote P2P host.
 *
 * <p>Uses ChannelInitializer to ensure pipeline is configured BEFORE channelActive
 * fires — same pattern as 26.2's Connection.fromChannel + acceptChannel.
 *
 * <p>1.16.5 Yarn differences from 26.1.2:
 * <ul>
 *   <li>No BandwidthDebugMonitor — setupSerialization takes 2 args (pipeline, side)</li>
 *   <li>No configurePacketHandler — uses setPacketListener directly</li>
 *   <li>No initiateServerboundPlayConnection — uses setPacketListener(loginHandler) + send(hello)</li>
 *   <li>No pendingConnection field — connection tracked via login handler</li>
 *   <li>No LevelLoadTracker</li>
 *   <li>ServerInfo uses (String, String, boolean) constructor (not ServerData.Type enum)</li>
 *   <li>ClientLoginNetworkHandler instead of ClientHandshakePacketListenerImpl</li>
 *   <li>disconnect() instead of disconnectWithProgressScreen()</li>
 * </ul>
 */
public final class ConnectionBridge {
    private static final Logger LOGGER = Logging.get();
    private static final DefaultEventLoopGroup EVENT_LOOP_GROUP = new DefaultEventLoopGroup(1, r -> {
        Thread t = new Thread(r, "msf-rtc-netty"); t.setDaemon(true); return t;
    });

    public static void joinHost(Channel rtcChannel, String playerName, UUID profileId) throws Exception {
        LOGGER.info("[connection-bridge] joinHost starting for player={} uuid={}", playerName, profileId);

        // --- resolve classes via FabricReflect (Yarn names) ---
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.ClientConnection");
        Class<?> networkSideClass = FabricReflect.mcClass("net.minecraft.network.NetworkSide");
        Class<?> mcClass  = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
        Class<?> loginHandlerClass = FabricReflect.mcClass("net.minecraft.client.network.ClientLoginNetworkHandler");
        Class<?> serverInfoClass = FabricReflect.mcClass("net.minecraft.client.network.ServerInfo");
        Class<?> helloPacketClass = FabricReflect.mcClass("net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket");
        Class<?> packetClass = FabricReflect.mcClass("net.minecraft.network.Packet");
        Class<?> loginListenerClass = FabricReflect.mcClass("net.minecraft.network.listener.ClientLoginPacketListener");

        // --- get MinecraftClient instance ---
        Object minecraft = FabricReflect.mcMethod(mcClass, "getInstance").invoke(null);

        // --- disconnect current world if any (mirrors 26.2 RtcHandshakeHandler.joinHost) ---
        // 1.16.5: no disconnectWithProgressScreen, use disconnect() + clear world
        try {
            Field worldField = FabricReflect.mcField(mcClass, "world");
            worldField.setAccessible(true);
            Object server = FabricReflect.mcMethod(mcClass, "getServer").invoke(minecraft);
            if (worldField.get(minecraft) != null || server != null) {
                try {
                    mcClass.getMethod("disconnect").invoke(minecraft);
                } catch (NoSuchMethodException e) {
                    worldField.set(minecraft, null);
                }
            }
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[connection-bridge] no world field: {}", e.getMessage());
        }

        // --- prepare all objects needed inside the ChannelInitializer ---
        Object clientbound = Enum.valueOf((Class<Enum>) networkSideClass, "CLIENTBOUND");

        Method setupSerialization = connectionClass.getMethod("setupSerialization", ChannelPipeline.class, networkSideClass);
        Method send = FabricReflect.mcMethod(connectionClass, "send", packetClass);

        // 1.16.5: ServerInfo(String name, String address, boolean isLAN)
        Constructor<?> serverInfoCtor = serverInfoClass.getConstructor(String.class, String.class, boolean.class);
        Constructor<?> helloCtor = helloPacketClass.getConstructor(String.class, UUID.class);

        // 1.16.5: ClientLoginNetworkHandler(ClientConnection, MinecraftClient, ServerInfo)
        Constructor<?> loginHandlerCtor = loginHandlerClass.getConstructor(connectionClass, mcClass, serverInfoClass);

        // 1.16.5: setPacketListener — equivalent of configurePacketHandler + initiateServerboundPlayConnection
        Method setPacketListener = connectionClass.getMethod("setPacketListener", loginListenerClass);

        // Capture finals for inner class
        final Object fMinecraft = minecraft;
        final Method fSetupSerialization = setupSerialization;
        final Object fClientbound = clientbound;
        final Method fSend = send;
        final Method fSetPacketListener = setPacketListener;
        final Constructor<?> fServerInfoCtor = serverInfoCtor;
        final Constructor<?> fHelloCtor = helloCtor;
        final Constructor<?> fLoginHandlerCtor = loginHandlerCtor;

        // --- ChannelInitializer: configure everything BEFORE channelActive ---
        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                // Create Connection WITHOUT setting channel field.
                // ClientConnection.channelActive will set it when the channel becomes active.
                Object connection = ConnectionFactory.createUnbound(
                        ConnectionFactory.PacketFlowDirection.CLIENTBOUND);

                // Configure pipeline — 1.16.5: setupSerialization only, no configurePacketHandler
                ChannelPipeline pipeline = ch.pipeline();
                fSetupSerialization.invoke(null, pipeline, fClientbound);

                // Create login handler (equivalent of 26.1.2's ClientHandshakePacketListenerImpl)
                // setPacketListener + ClientLoginNetworkHandler = 26.1.2's
                // configurePacketHandler + initiateServerboundPlayConnection
                try {
                    Object serverInfo = fServerInfoCtor.newInstance("Online", "rtc-peer", false);
                    Object loginHandler = fLoginHandlerCtor.newInstance(connection, fMinecraft, serverInfo);
                    fSetPacketListener.invoke(connection, loginHandler);
                } catch (Exception e) {
                    LOGGER.warn("[connection-bridge] failed to set login listener", e);
                }

                // Queue hello packet send
                try {
                    Object helloPacket = fHelloCtor.newInstance(playerName, profileId);
                    fSend.invoke(connection, helloPacket);
                } catch (Exception e) {
                    LOGGER.warn("[connection-bridge] failed to send hello", e);
                }

                LOGGER.info("[connection-bridge] initChannel complete, pipeline ready");
            }
        });

        // --- Register: triggers initChannel -> channelRegistered -> channelActive ---
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();

        LOGGER.info("[connection-bridge] joinHost complete — connection established");
    }
}