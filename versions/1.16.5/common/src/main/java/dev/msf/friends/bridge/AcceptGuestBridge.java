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

public final class AcceptGuestBridge {
    private static final Logger LOGGER = Logging.get();
    private static final DefaultEventLoopGroup EVENT_LOOP_GROUP = new DefaultEventLoopGroup(1, r -> {
        Thread t = new Thread(r, "msf-rtc-server-netty"); t.setDaemon(true); return t;
    });

    public static void acceptGuest(Channel rtcChannel, UUID guestProfileId) throws Exception {
        LOGGER.info("[accept-guest] accepting guest profileId={}", guestProfileId);

        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.ClientConnection");
        Class<?> networkSideClass = FabricReflect.mcClass("net.minecraft.network.NetworkSide");
        Class<?> mcClass = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
        Class<?> serverClass = FabricReflect.mcClass("net.minecraft.server.MinecraftServer");
        Class<?> sclClass = FabricReflect.mcClass("net.minecraft.server.ServerNetworkIo");
        Class<?> handshakeClass = FabricReflect.mcClass("net.minecraft.server.network.ServerHandshakeNetworkHandler");
        Class<?> listenerClass = FabricReflect.mcClass("net.minecraft.network.listener.PacketListener");

        Object minecraft = FabricReflect.mcMethod(mcClass, "getInstance").invoke(null);
        Object server = FabricReflect.mcMethod(mcClass, "getServer").invoke(minecraft);
        if (server == null) { rtcChannel.close(); return; }

        Field sclField = FabricReflect.mcField(serverClass, "networkIo");
        sclField.setAccessible(true);
        Object scl = sclField.get(server);
        if (scl == null) { rtcChannel.close(); return; }

        @SuppressWarnings("unchecked")
        List<Object> connections = (List<Object>) FabricReflect.mcMethod(sclClass, "getConnections").invoke(scl);

        rtcChannel.attr(RtcChannel.INTENDED_PROFILE_ID).set(guestProfileId);

        Object serverbound = Enum.valueOf((Class<Enum>) networkSideClass, "SERVERBOUND");
        Method setupSerialization = connectionClass.getMethod("setupSerialization", ChannelPipeline.class, networkSideClass);
        Constructor<?> handshakeCtor = handshakeClass.getConstructor(serverClass, connectionClass);
        Method setListener = FabricReflect.mcMethod(connectionClass, "setPacketListener", listenerClass);

        final Object fServer = server;
        final Object fServerbound = serverbound;
        final List<Object> fConnections = connections;
        final Method fSetupSerialization = setupSerialization;
        final Constructor<?> fHandshakeCtor = handshakeCtor;
        final Method fSetListener = setListener;

        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                Object connection = ConnectionFactory.createUnbound(
                        ConnectionFactory.PacketFlowDirection.SERVERBOUND);
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                fSetupSerialization.invoke(null, pipeline, fServerbound);

                Object handshakeListener = fHandshakeCtor.newInstance(fServer, connection);
                fSetListener.invoke(connection, handshakeListener);

                synchronized (fConnections) { fConnections.add(connection); }
                LOGGER.info("[accept-guest] guest connection accepted");
            }
        });

        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
    }
}