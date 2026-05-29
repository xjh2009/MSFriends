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
 * Client-side bridge for MC 1.17.1: wires an RtcChannel into MC's Connection.
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

        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        Class<?> packetFlowClass = FabricReflect.mcClass("net.minecraft.network.protocol.PacketFlow");
        Class<?> minecraftClass  = FabricReflect.mcClass("net.minecraft.client.Minecraft");
        Class<?> clientHandshakeClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl");
        Class<?> serverDataClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.ServerData");
        Class<?> helloPacketClass = FabricReflect.mcClass("net.minecraft.network.protocol.login.ServerboundHelloPacket");
        Class<?> packetClass = FabricReflect.mcClass("net.minecraft.network.protocol.Packet");
        Class<?> clientLoginListenerClass = FabricReflect.mcClass("net.minecraft.network.protocol.login.ClientLoginPacketListener");

        Object minecraft = FabricReflect.mcMethod(minecraftClass, "getInstance").invoke(null);

        try {
            Field levelField = FabricReflect.mcField(minecraftClass, "level");
            levelField.setAccessible(true);
            if (levelField.get(minecraft) != null) {
                try { minecraftClass.getMethod("disconnect").invoke(minecraft); }
                catch (NoSuchMethodException e) {
                    try { minecraftClass.getMethod("clearLevel").invoke(minecraft); }
                    catch (NoSuchMethodException e2) {
                        LOGGER.debug("[connection-bridge] no disconnect/clearLevel method");
                    }
                }
            }
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[connection-bridge] no level field: {}", e.getMessage());
        }

        Object clientbound = Enum.valueOf((Class<Enum>) packetFlowClass, "CLIENTBOUND");
        final Method configureSerialization = resolveMethod(connectionClass,
                "configureSerialization", "setupSerialization",
                ChannelPipeline.class, packetFlowClass, boolean.class);
        final Method send = FabricReflect.mcMethod(connectionClass, "send", packetClass);

        final Constructor<?> serverDataCtor = resolveCtor(serverDataClass,
                new Class<?>[]{String.class, String.class, boolean.class},
                new Class<?>[]{String.class, String.class});

        final Constructor<?> helloCtor = resolveCtor(helloPacketClass,
                new Class<?>[]{String.class, java.util.Optional.class},
                new Class<?>[]{String.class, UUID.class},
                new Class<?>[]{String.class});

        final Constructor<?> handshakeCtor = clientHandshakeClass.getConstructors()[0];
        final Method initiateConnection = resolveInitiateConnection(connectionClass, clientLoginListenerClass);
        final Object fMinecraft = minecraft;

        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                Object connection = ConnectionFactory.createUnbound(
                        ConnectionFactory.PacketFlowDirection.CLIENTBOUND);

                ChannelPipeline pipeline = ch.pipeline();
                configureSerialization.invoke(null, pipeline, clientbound, false);

                try {
                    Method setListener = connectionClass.getMethod("setListener", clientLoginListenerClass);
                    Object serverData;
                    try { serverData = serverDataCtor.newInstance("Online", "rtc-peer", false); }
                    catch (Exception e) { serverData = serverDataCtor.newInstance("Online", "rtc-peer"); }

                    Object handshakeListener = handshakeCtor.newInstance(connection, fMinecraft, serverData);
                    setListener.invoke(connection, handshakeListener);
                } catch (Exception e) {
                    LOGGER.warn("[connection-bridge] failed to set listener", e);
                }

                try {
                    Object helloPacket;
                    try { helloPacket = helloCtor.newInstance(playerName, java.util.Optional.of(profileId)); }
                    catch (Exception e) {
                        try { helloPacket = helloCtor.newInstance(playerName, profileId); }
                        catch (Exception e2) { helloPacket = helloCtor.newInstance(playerName); }
                    }
                    send.invoke(connection, helloPacket);
                } catch (Exception e) {
                    LOGGER.warn("[connection-bridge] failed to send hello packet", e);
                }

                try {
                    Field pendingField = FabricReflect.mcField(minecraftClass, "pendingConnection");
                    pendingField.setAccessible(true);
                    pendingField.set(fMinecraft, connection);
                } catch (NoSuchFieldException e) {
                    LOGGER.debug("[connection-bridge] no pendingConnection field");
                }

                LOGGER.info("[connection-bridge] initChannel complete, pipeline ready");
            }
        });

        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
        LOGGER.info("[connection-bridge] joinHost complete — connection established");
    }

    private static Method resolveMethod(Class<?> owner, String primary, String fallback, Class<?>... paramTypes) throws NoSuchMethodException {
        try { return FabricReflect.mcMethod(owner, primary, paramTypes); }
        catch (NoSuchMethodException e) { return FabricReflect.mcMethod(owner, fallback, paramTypes); }
    }

    private static Constructor<?> resolveCtor(Class<?> clazz, Class<?>[]... signatures) {
        for (Class<?>[] sig : signatures) {
            try { return clazz.getConstructor(sig); } catch (NoSuchMethodException ignored) {}
        }
        return clazz.getConstructors()[0];
    }

    private static Method resolveInitiateConnection(Class<?> connectionClass, Class<?> clientLoginListenerClass) throws NoSuchMethodException {
        try { return FabricReflect.mcMethod(connectionClass, "initiateServerboundPlayConnection", String.class, int.class, clientLoginListenerClass); }
        catch (NoSuchMethodException e) {
            try { return FabricReflect.mcMethod(connectionClass, "setListenerForServerboundHandshake", clientLoginListenerClass); }
            catch (NoSuchMethodException e2) { return FabricReflect.mcMethod(connectionClass, "initiateConnection", String.class, int.class, clientLoginListenerClass); }
        }
    }
}
