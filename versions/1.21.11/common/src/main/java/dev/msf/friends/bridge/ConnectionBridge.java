package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Class<?> packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Class<?> minecraftClass  = Class.forName("net.minecraft.client.Minecraft");
        Class<?> bandwidthMonitorClass = Class.forName("net.minecraft.network.BandwidthDebugMonitor");
        Class<?> localSampleLoggerClass = Class.forName("net.minecraft.util.debugchart.LocalSampleLogger");
        Class<?> clientHandshakeClass = Class.forName("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl");
        Class<?> serverDataClass = Class.forName("net.minecraft.client.multiplayer.ServerData");
        Class<?> serverDataTypeClass = Class.forName("net.minecraft.client.multiplayer.ServerData$Type");
        Class<?> helloPacketClass = Class.forName("net.minecraft.network.protocol.login.ServerboundHelloPacket");
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> levelLoadTrackerClass = Class.forName("net.minecraft.client.multiplayer.LevelLoadTracker");
        Class<?> debugOverlayClass = Class.forName("net.minecraft.client.gui.components.DebugScreenOverlay");
        Class<?> clientLoginListenerClass = Class.forName("net.minecraft.network.protocol.login.ClientLoginPacketListener");
        Class<?> packetListenerClass = Class.forName("net.minecraft.network.PacketListener");

        // --- get Minecraft instance ---
        Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);

        // --- disconnect current world if any (mirrors 26.2 RtcHandshakeHandler.joinHost) ---
        try {
            Field levelField = minecraftClass.getDeclaredField("level");
            levelField.setAccessible(true);
            Object singleplayerServer = minecraftClass.getMethod("getSingleplayerServer").invoke(minecraft);
            if (levelField.get(minecraft) != null || singleplayerServer != null) {
                minecraftClass.getMethod("disconnectWithProgressScreen", boolean.class).invoke(minecraft, false);
            }
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            LOGGER.debug("[connection-bridge] no level/disconnect: {}", e.getMessage());
        }

        // --- prepare all objects needed inside the ChannelInitializer ---
        Object clientbound = Enum.valueOf((Class<Enum>) packetFlowClass, "CLIENTBOUND");

        Method getDebugOverlay = minecraftClass.getMethod("getDebugOverlay");
        Object debugOverlay = getDebugOverlay.invoke(minecraft);
        Object bandwidthLogger = debugOverlayClass.getMethod("getBandwidthLogger").invoke(debugOverlay);
        Constructor<?> monitorCtor = bandwidthMonitorClass.getConstructor(localSampleLoggerClass);
        Object monitor = monitorCtor.newInstance(bandwidthLogger);

        Method configureSerialization = connectionClass.getMethod("configureSerialization",
                ChannelPipeline.class, packetFlowClass, boolean.class, bandwidthMonitorClass);
        Method configurePacketHandler = connectionClass.getMethod("configurePacketHandler", ChannelPipeline.class);
        Method initPlay = connectionClass.getMethod("initiateServerboundPlayConnection",
                String.class, int.class, clientLoginListenerClass);
        Method send = connectionClass.getMethod("send", packetClass);

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

        Field pendingField = minecraftClass.getDeclaredField("pendingConnection");
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
                                    LOGGER.info("[connection-bridge] Forge NetworkRegistry.onConnectionStart called successfully");
                                } catch (ClassNotFoundException e) {
                                    // Not Forge — skip
                                } catch (Throwable t) {
                                    LOGGER.warn("[connection-bridge] Failed to call Forge NetworkRegistry.onConnectionStart", t);
                                }
                                // Remove self after firing
                                ctx.pipeline().remove(this);
                            });
                        }
                    }
                });

                LOGGER.info("[connection-bridge] initChannel complete, pipeline ready");
            }
        });

        // --- Register: triggers initChannel → channelRegistered → channelActive ---
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();

        LOGGER.info("[connection-bridge] joinHost complete — connection established");
    }
}
