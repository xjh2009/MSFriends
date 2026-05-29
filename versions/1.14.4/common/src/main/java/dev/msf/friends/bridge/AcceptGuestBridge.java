package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

/**
 * Server-side bridge that accepts a P2P guest connection on an integrated server.
 *
 * <p>1.14.4: Uses the same pattern as 1.16.5 but with 1.14.4 API signatures.
 */
public final class AcceptGuestBridge {
    private static final Logger LOGGER = Logging.get();

    /** Set by ServerLoginMixin so the guest-acceptor can access the profile. */
    public static volatile GameProfile pendingGuestProfile;

    private AcceptGuestBridge() {}

    public static void acceptGuest(io.netty.channel.Channel rtcChannel, UUID guestProfileId) throws Exception {
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        Class<?> packetFlowClass = FabricReflect.mcClass("net.minecraft.network.protocol.PacketFlow");

        Object serverbound = Enum.valueOf((Class<Enum>) packetFlowClass, "SERVERBOUND");
        Object connection = ConnectionFactory.create(rtcChannel, serverbound);
        configureSerialization(rtcChannel, serverbound, true);

        rtcChannel.pipeline().addLast("timeout",
                new io.netty.handler.timeout.ReadTimeoutHandler(30));

        setupServerHandshake(connection, guestProfileId);
        addToServerConnections(connection);

        LOGGER.info("[bridge] Accepted P2P guest connection for {}", guestProfileId);
    }

    private static void configureSerialization(io.netty.channel.Channel channel, Object packetFlow, boolean isServer) throws Exception {
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        try {
            Method configure = connectionClass.getMethod("configureSerialization",
                    io.netty.channel.Channel.class, packetFlow.getClass(), boolean.class);
            configure.invoke(null, channel, packetFlow, isServer);
        } catch (NoSuchMethodException e) {
            LOGGER.warn("[bridge] configureSerialization(3-arg) not found, trying fallback");
            try {
                Method configure = connectionClass.getMethod("configureSerialization",
                        io.netty.channel.Channel.class, packetFlow.getClass());
                configure.invoke(null, channel, packetFlow);
            } catch (NoSuchMethodException e2) {
                LOGGER.error("[bridge] Could not find configureSerialization method");
            }
        }
    }

    private static void setupServerHandshake(Object connection, UUID guestProfileId) throws Exception {
        Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
        Object mcInstance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
        if (mcInstance == null) {
            LOGGER.warn("[bridge] Cannot setup server handshake: Minecraft instance not available");
            return;
        }

        Object server = FabricReflect.mcMethod(mc, "getSingleplayerServer").invoke(mcInstance);
        if (server == null) {
            LOGGER.warn("[bridge] Cannot setup server handshake: integrated server not available");
            return;
        }

        try {
            Class<?> handshakeListenerClass = FabricReflect.mcClass(
                    "net.minecraft.server.network.ServerHandshakePacketListenerImpl");
            Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");

            Constructor<?> ctor = handshakeListenerClass.getConstructor(server.getClass(), connectionClass);
            Object listener = ctor.newInstance(server, connection);

            Method setListener = connectionClass.getMethod("setListener",
                    FabricReflect.mcClass("net.minecraft.network.PacketListener"));
            setListener.invoke(connection, listener);
        } catch (Exception e) {
            LOGGER.warn("[bridge] Could not setup server handshake listener: {}", e.getMessage());
        }
    }

    private static void addToServerConnections(Object connection) throws Exception {
        Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
        Object mcInstance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
        Object server = FabricReflect.mcMethod(mc, "getSingleplayerServer").invoke(mcInstance);

        Class<?> serverClass = server.getClass();
        Object serverConn = FabricReflect.mcField(serverClass, "connection").get(server);

        Method getConnections = FabricReflect.mcMethod(serverConn.getClass(), "getConnections");
        @SuppressWarnings("unchecked")
        java.util.List<Object> connections = (java.util.List<Object>) getConnections.invoke(serverConn);
        synchronized (connections) {
            connections.add(connection);
        }
    }
}
