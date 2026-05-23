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
 *
 * Uses ChannelInitializer to ensure pipeline is configured BEFORE channelActive
 * fires — same pattern as 26.2's Connection.fromChannel + acceptChannel.
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
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        Class<?> packetFlowClass = FabricReflect.mcClass("net.minecraft.network.protocol.PacketFlow");
        Class<?> minecraftClass  = FabricReflect.mcClass("net.minecraft.client.Minecraft");
        Class<?> bandwidthMonitorClass = FabricReflect.mcClass("net.minecraft.network.BandwidthDebugMonitor");
        Class<?> localSampleLoggerClass = FabricReflect.mcClass("net.minecraft.util.debugchart.LocalSampleLogger");
        Class<?> clientHandshakeClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl");
        Class<?> serverDataClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.ServerData");
        Class<?> serverDataTypeClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.ServerData$Type");
        Class<?> helloPacketClass = FabricReflect.mcClass("net.minecraft.network.protocol.login.ServerboundHelloPacket");
        Class<?> packetClass = FabricReflect.mcClass("net.minecraft.network.protocol.Packet");
        Class<?> levelLoadTrackerClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.LevelLoadTracker");
        Class<?> debugOverlayClass = FabricReflect.mcClass("net.minecraft.client.gui.components.DebugScreenOverlay");
        Class<?> clientLoginListenerClass = FabricReflect.mcClass("net.minecraft.network.protocol.login.ClientLoginPacketListener");
        Class<?> packetListenerClass = FabricReflect.mcClass("net.minecraft.network.PacketListener");

        // --- get Minecraft instance ---
        Object minecraft = FabricReflect.mcMethod(minecraftClass, "getInstance").invoke(null);

        // --- disconnect current world if any (mirrors 26.2 RtcHandshakeHandler.joinHost) ---
        try {
            Field levelField = FabricReflect.mcField(minecraftClass, "level");
            levelField.setAccessible(true);
            Object singleplayerServer = FabricReflect.mcMethod(minecraftClass, "getSingleplayerServer").invoke(minecraft);
            if (levelField.get(minecraft) != null || singleplayerServer != null) {
                FabricReflect.mcMethod(minecraftClass, "disconnectWithProgressScreen", boolean.class).invoke(minecraft, false);
            }
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            LOGGER.debug("[connection-bridge] no level/disconnect: {}", e.getMessage());
        }

        // --- prepare all objects needed inside the ChannelInitializer ---
        Object clientbound = Enum.valueOf((Class<Enum>) packetFlowClass, "CLIENTBOUND");

        Method getDebugOverlay = FabricReflect.mcMethod(minecraftClass, "getDebugOverlay");
        Object debugOverlay = getDebugOverlay.invoke(minecraft);
        Object bandwidthLogger = FabricReflect.mcMethod(debugOverlayClass, "getBandwidthLogger").invoke(debugOverlay);
        Constructor<?> monitorCtor = bandwidthMonitorClass.getConstructor(localSampleLoggerClass);
        Object monitor = monitorCtor.newInstance(bandwidthLogger);

        Method configureSerialization = FabricReflect.mcMethod(connectionClass, "configureSerialization",
                ChannelPipeline.class, packetFlowClass, boolean.class, bandwidthMonitorClass);
        Method configurePacketHandler = FabricReflect.mcMethod(connectionClass, "configurePacketHandler", ChannelPipeline.class);
        Method initPlay = FabricReflect.mcMethod(connectionClass, "initiateServerboundPlayConnection",
                String.class, int.class, clientLoginListenerClass);
        Method send = FabricReflect.mcMethod(connectionClass, "send", packetClass);

        Object serverDataTypeLAN = Enum.valueOf((Class<Enum>) serverDataTypeClass, "LAN");
        Constructor<?> serverDataCtor = serverDataClass.getConstructor(String.class, String.class, serverDataTypeClass);
        Object serverData = serverDataCtor.newInstance("Online", "rtc-peer", serverDataTypeLAN);
        Object levelLoadTracker = levelLoadTrackerClass.getConstructor().newInstance();

        // No-op status consumer (lambda var0 -> {} equivalent via proxy)
        Object noOpConsumer = java.lang.reflect.Proxy.newProxyInstance(
                java.util.function.Consumer.class.getClassLoader(),
                new Class<?>[]{ java.util.function.Consumer.class },
                (proxy, method, args) -> null);

        Constructor<?> handshakeCtor = clientHandshakeClass.getConstructors()[0];
        Constructor<?> helloCtor = helloPacketClass.getConstructor(String.class, UUID.class);

        Field pendingField = FabricReflect.mcField(minecraftClass, "pendingConnection");
        pendingField.setAccessible(true);

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

                // Configure pipeline
                ChannelPipeline pipeline = ch.pipeline();
                configureSerialization.invoke(null, pipeline, clientbound, false, monitor);
                configurePacketHandler.invoke(connection, pipeline);

                // Create login listener
                Object handshakeListener = handshakeCtor.newInstance(
                        connection, fMinecraft, serverData,
                        null,           // parent screen
                        false,          // is transfer
                        null,           // timeout Duration
                        noOpConsumer,   // status consumer
                        levelLoadTracker,
                        null            // TransferState
                );

                // initiateServerboundPlayConnection — registers runOnceConnected callback.
                // channelActive hasn't fired yet (we're inside initChannel, before registration completes),
                // so the callback will correctly fire when channelActive happens after this returns.
                initPlay.invoke(connection, "rtc-peer", 0, handshakeListener);

                // Queue hello packet send
                send.invoke(connection, helloCtor.newInstance(playerName, profileId));

                // Set pending connection
                pendingField.set(fMinecraft, connection);

                LOGGER.info("[connection-bridge] initChannel complete, pipeline ready");
            }
        });

        // --- Register: triggers initChannel → channelRegistered → channelActive ---
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();

        LOGGER.info("[connection-bridge] joinHost complete — connection established");
    }
}
