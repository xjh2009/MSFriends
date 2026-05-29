package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Client-side bridge that wires an RtcChannel into a new {@code Connection}
 * so the client connects to the remote LAN host via the P2P pipe.
 *
 * <p>1.14.4 differences from 1.16.5:
 * <ul>
 *   <li>No BandwidthDebugMonitor in configureSerialization (3 params instead of 4)</li>
 *   <li>ServerData constructor may differ</li>
 *   <li>No disconnectWithProgressScreen</li>
 * </ul>
 */
public final class ConnectionBridge {
    private static final Logger LOGGER = Logging.get();

    private ConnectionBridge() {}

    /**
     * Called on the main client thread when the P2P connection is ready.
     */
    public static void joinHost(io.netty.channel.Channel rtcChannel, String guestName, UUID guestId) throws Exception {
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        Class<?> packetFlowClass = FabricReflect.mcClass("net.minecraft.network.protocol.PacketFlow");

        Object clientbound = Enum.valueOf((Class<Enum>) packetFlowClass, "CLIENTBOUND");
        Object connection = ConnectionFactory.create(rtcChannel, clientbound);
        configureSerialization(rtcChannel, clientbound, false);

        Object serverData = buildServerData(guestName);

        Class<?> handshakeListenerClass = FabricReflect.mcClass(
                "net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl");
        Object mcInstance = getMinecraftInstance();
        Constructor<?> handshakeCtor = handshakeListenerClass.getConstructor(
                connectionClass,
                FabricReflect.mcClass("net.minecraft.client.Minecraft"),
                FabricReflect.mcClass("net.minecraft.client.gui.screens.Screen"),
                serverData.getClass());
        Object handshakeListener = handshakeCtor.newInstance(connection, mcInstance, null, serverData);

        Class<?> helloPacketClass = FabricReflect.mcClass("net.minecraft.network.protocol.login.ServerboundHelloPacket");
        Object helloPacket = helloPacketClass.getConstructor(UUID.class, String.class).newInstance(guestId, guestName);

        Method send = connectionClass.getMethod("send",
                FabricReflect.mcClass("net.minecraft.network.protocol.Packet"));
        send.invoke(connection, helloPacket);

        setPendingConnection(mcInstance, connection);

        LOGGER.info("[bridge] Client connected to remote host via P2P");
    }

    private static void configureSerialization(io.netty.channel.Channel channel, Object packetFlow, boolean isServer) throws Exception {
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        try {
            Method configure = connectionClass.getMethod("configureSerialization",
                    io.netty.channel.Channel.class, packetFlow.getClass(), boolean.class);
            configure.invoke(null, channel, packetFlow, isServer);
        } catch (NoSuchMethodException e) {
            Class<?> connClass = FabricReflect.mcClass("net.minecraft.network.Connection");
            Method configure = connClass.getMethod("configureSerialization",
                    io.netty.channel.Channel.class, packetFlow.getClass());
            configure.invoke(null, channel, packetFlow);
        }
    }

    private static Object buildServerData(String serverName) throws Exception {
        Class<?> serverDataClass = FabricReflect.mcClass("net.minecraft.client.multiplayer.ServerData");
        Constructor<?> ctor = serverDataClass.getConstructor(String.class, String.class);
        return ctor.newInstance(serverName, "msf-friends-p2p");
    }

    private static Object getMinecraftInstance() throws Exception {
        Class<?> mcClass = FabricReflect.mcClass("net.minecraft.client.Minecraft");
        return FabricReflect.mcMethod(mcClass, "getInstance").invoke(null);
    }

    private static void setPendingConnection(Object mcInstance, Object connection) throws Exception {
        Class<?> mcClass = FabricReflect.mcClass("net.minecraft.client.Minecraft");
        try {
            Field pendingField = FabricReflect.mcField(mcClass, "pendingConnection");
            pendingField.setAccessible(true);
            pendingField.set(mcInstance, connection);
        } catch (NoSuchFieldException e) {
            LOGGER.warn("[bridge] pendingConnection field not found, connection may not wire correctly");
        }
    }
}
