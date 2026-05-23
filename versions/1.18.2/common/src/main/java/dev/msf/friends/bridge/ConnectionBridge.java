package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
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
import java.util.UUID;

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
        Class<?> minecraftClass = FabricReflect.mcClass("net.minecraft.client.Minecraft");
        Class<?> clientHandshakeClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl");
        Class<?> serverDataClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.ServerData");
        Class<?> helloPacketClass = FabricReflect.mcClass("net.minecraft.network.protocol.login.ServerboundHelloPacket");
        Class<?> packetClass = FabricReflect.mcClass("net.minecraft.network.protocol.Packet");
        Class<?> packetListenerClass = FabricReflect.mcClass("net.minecraft.network.PacketListener");

        // 1.18.2: no configureSerialization — resolve codec classes for inline pipeline
        Class<?> packetDecoderClass = Class.forName("net.minecraft.network.PacketDecoder");
        Class<?> packetEncoderClass = Class.forName("net.minecraft.network.PacketEncoder");
        Class<?> varintDecoderClass = Class.forName("net.minecraft.network.Varint21FrameDecoder");
        Class<?> varintPrependerClass = Class.forName("net.minecraft.network.Varint21LengthFieldPrepender");

        Object minecraft = FabricReflect.mcMethod(minecraftClass, "getInstance").invoke(null);

        // disconnect current world if any
        try {
            Field levelField = FabricReflect.mcField(minecraftClass, "level");
            levelField.setAccessible(true);
            Object spServer = FabricReflect.mcMethod(minecraftClass, "getSingleplayerServer").invoke(minecraft);
            if (levelField.get(minecraft) != null || spServer != null) {
                try { FabricReflect.mcMethod(minecraftClass, "disconnect").invoke(minecraft); }
                catch (NoSuchMethodException e) { FabricReflect.mcMethod(minecraftClass, "clearLevel").invoke(minecraft); }
            }
        } catch (Exception e) { LOGGER.debug("[connection-bridge] no level/disconnect: {}", e.getMessage()); }

        Object clientbound = Enum.valueOf((Class<Enum>) packetFlowClass, "CLIENTBOUND");
        Object serverbound = Enum.valueOf((Class<Enum>) packetFlowClass, "SERVERBOUND");

        Method send = FabricReflect.mcMethod(connectionClass, "send", packetClass);
        Method setListener = FabricReflect.mcMethod(connectionClass, "setPacketListener", packetListenerClass);

        Constructor<?> helloCtor = helloPacketClass.getConstructor(String.class, UUID.class);
        Constructor<?> serverDataCtor = serverDataClass.getConstructor(String.class, String.class, boolean.class);
        Object serverData = serverDataCtor.newInstance("Online", "rtc-peer", false);
        final Constructor<?> handshakeCtor = findCtor(clientHandshakeClass);
        Field pendingField = FabricReflect.mcField(minecraftClass, "pendingConnection");
        pendingField.setAccessible(true);

        final Object fMinecraft = minecraft;
        final Object fClientbound = clientbound;
        final Object fServerbound = serverbound;

        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                Object connection = ConnectionFactory.createUnbound(ConnectionFactory.PacketFlowDirection.CLIENTBOUND);

                // 1.18.2 inline pipeline — mirrors Connection$1.initChannel
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                pipeline.addLast("splitter", (ChannelHandler) varintDecoderClass.getDeclaredConstructor().newInstance());
                pipeline.addLast("decoder", (ChannelHandler) packetDecoderClass.getDeclaredConstructor(packetFlowClass).newInstance(fClientbound));
                pipeline.addLast("prepender", (ChannelHandler) varintPrependerClass.getDeclaredConstructor().newInstance());
                pipeline.addLast("encoder", (ChannelHandler) packetEncoderClass.getDeclaredConstructor(packetFlowClass).newInstance(fServerbound));
                pipeline.addLast("packet_handler", (ChannelHandler) connection);

                Class<?>[] ptypes = handshakeCtor.getParameterTypes();
                Object[] args = new Object[ptypes.length];
                for (int i = 0; i < ptypes.length; i++) {
                    if (ptypes[i] == connectionClass) args[i] = connection;
                    else if (ptypes[i] == minecraftClass) args[i] = fMinecraft;
                    else if (ptypes[i] == serverDataClass) args[i] = serverData;
                    else if (ptypes[i] == String.class) args[i] = "rtc-peer";
                    else if (ptypes[i] == int.class || ptypes[i] == Integer.class) args[i] = 0;
                    else if (ptypes[i] == boolean.class || ptypes[i] == Boolean.class) args[i] = false;
                    else args[i] = null;
                }
                Object handshakeListener = handshakeCtor.newInstance(args);
                setListener.invoke(connection, handshakeListener);
                send.invoke(connection, helloCtor.newInstance(playerName, profileId));
                pendingField.set(fMinecraft, connection);

                // Forge init (no-op on Fabric)
                pipeline.addLast("forge-init", new ChannelInboundHandlerAdapter() {
                    boolean fired = false;
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        ctx.fireChannelActive();
                        if (!fired) {
                            fired = true;
                            ctx.executor().execute(() -> {
                                try {
                                    Class<?> nrc = Class.forName("net.minecraftforge.network.NetworkRegistry");
                                    nrc.getMethod("onConnectionStart", connectionClass).invoke(null, connection);
                                } catch (ClassNotFoundException ignored) {}
                                catch (Throwable t) { LOGGER.warn("[connection-bridge] Forge init failed", t); }
                                ctx.pipeline().remove(this);
                            });
                        }
                    }
                });
                LOGGER.info("[connection-bridge] initChannel complete, pipeline ready");
            }
        });

        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
        LOGGER.info("[connection-bridge] joinHost complete");
    }

    private static Constructor<?> findCtor(Class<?> clazz) throws Exception {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) { if (c.getParameterCount() >= 2) return c; }
        throw new RuntimeException("Cannot find ClientHandshakePacketListenerImpl constructor");
    }
}
