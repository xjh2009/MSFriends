package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Equivalent of 26.2's {@code Connection.fromChannel(Channel, PacketFlow)}.
 * For MC 1.19.2: no BandwidthDebugMonitor or LevelLoadTracker support.
 *
 * <p>Creates a Connection bound to an existing channel, with the channel and
 * address fields already set.
 */
public final class ConnectionFactory {
    private static final Logger LOGGER = Logging.get();

    // Cached reflection handles
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
        connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        packetFlowClass = FabricReflect.mcClass("net.minecraft.network.protocol.PacketFlow");
        connectionCtor = connectionClass.getConstructor(packetFlowClass);
        channelField = FabricReflect.mcField(connectionClass, "channel");
        channelField.setAccessible(true);
        addressField = FabricReflect.mcField(connectionClass, "address");
        addressField.setAccessible(true);
        resolved = true;
    }

    /** Direction enum mirroring PacketFlow without compile-time MC dependency. */
    public enum PacketFlowDirection {
        CLIENTBOUND,
        SERVERBOUND
    }
}
