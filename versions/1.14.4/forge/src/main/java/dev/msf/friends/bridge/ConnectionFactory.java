package dev.msf.friends.bridge;

import io.netty.channel.Channel;
import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Creates MC NetworkManager objects from Netty channels for MC 1.14.4 Forge.
 *
 * <p>1.13.2 MCP: {@code NetworkManager} replaces {@code Connection};
 * {@code NetworkDirection} / {@code PacketDirection} replaces {@code PacketFlow}.
 * Uses {@link ForgeReflect} for SRG fallback in production.
 */
public final class ConnectionFactory {
    private static final Logger LOGGER = Logging.get();

    private static volatile boolean resolved;
    private static Class<?> connectionClass;
    private static Class<?> packetFlowClass;
    private static Constructor<?> connectionCtor;
    private static Field channelField;
    private static Field addressField;

    public static Object fromChannel(Channel channel, PacketFlowDirection receiving) throws Exception {
        ensureResolved();
        Object flow = Enum.valueOf((Class<Enum>) packetFlowClass, receiving.name());
        Object connection = connectionCtor.newInstance(flow);
        channelField.set(connection, channel);
        addressField.set(connection, channel.remoteAddress());
        return connection;
    }

    public static Object createUnbound(PacketFlowDirection receiving) throws Exception {
        ensureResolved();
        Object flow = Enum.valueOf((Class<Enum>) packetFlowClass, receiving.name());
        return connectionCtor.newInstance(flow);
    }

    private static synchronized void ensureResolved() throws Exception {
        if (resolved) return;
        connectionClass = ForgeReflect.mcClass("net.minecraft.network.NetworkManager");
        // 1.13.2: EnumPacketDirection contains CLIENTBOUND / SERVERBOUND
        // (EnumConnectionState is the protocol state enum: HANDSHAKE, LOGIN, PLAY, STATUS)
        String[] directionCandidates = {
            "net.minecraft.network.EnumPacketDirection",
            "net.minecraft.network.NetworkDirection",
            "net.minecraft.network.protocol.PacketFlow"
        };
        for (String candidate : directionCandidates) {
            try {
                packetFlowClass = ForgeReflect.mcClass(candidate);
                // Verify it actually has CLIENTBOUND/SERVERBOUND values
                Enum.valueOf((Class<Enum>) packetFlowClass, "CLIENTBOUND");
                break;
            } catch (ClassNotFoundException | IllegalArgumentException ignored) {
                packetFlowClass = null;
            }
        }
        if (packetFlowClass == null) {
            throw new ClassNotFoundException("No direction enum found (tried EnumPacketDirection, NetworkDirection, PacketFlow)");
        }
        connectionCtor = connectionClass.getConstructor(packetFlowClass);
        channelField = ForgeReflect.mcField(connectionClass, "channel");
        channelField.setAccessible(true);
        addressField = ForgeReflect.mcField(connectionClass, "socketAddress");
        addressField.setAccessible(true);
        resolved = true;
    }

    public enum PacketFlowDirection {
        CLIENTBOUND,
        SERVERBOUND
    }
}
