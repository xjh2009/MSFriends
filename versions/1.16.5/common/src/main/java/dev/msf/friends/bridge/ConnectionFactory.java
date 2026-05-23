package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Equivalent of 26.2's {@code Connection.fromChannel(Channel, PacketFlow)}.
 *
 * <p>1.16.5 Yarn: uses ClientConnection / NetworkSide names resolved via FabricReflect.
 * Creates a ClientConnection bound to an existing channel, with the channel and
 * address fields already set. The caller is responsible for configuring the
 * pipeline and setting up protocol listeners afterward.
 *
 * <p>1.16.5 does NOT have BandwidthDebugMonitor, so the bandwidth-logger overload
 * from 26.1.2 is omitted.
 */
public final class ConnectionFactory {
    private static final Logger LOGGER = Logging.get();

    // Cached reflection handles (resolved once on first use)
    private static volatile boolean resolved;
    private static Class<?> connectionClass;
    private static Class<?> packetFlowClass;
    private static Constructor<?> connectionCtor;
    private static Field channelField;
    private static Field addressField;

    /**
     * Create a new ClientConnection bound to the given channel.
     * Sets both channel and address fields immediately.
     * Use this when the channel is already registered and active.
     */
    public static Object fromChannel(Channel channel, PacketFlowDirection receiving) throws Exception {
        ensureResolved();

        Object flow = Enum.valueOf((Class<Enum>) packetFlowClass, receiving.name());
        Object connection = connectionCtor.newInstance(flow);
        channelField.set(connection, channel);
        addressField.set(connection, channel.remoteAddress());

        return connection;
    }

    /**
     * Create a new ClientConnection WITHOUT setting the channel field.
     * Use this inside a ChannelInitializer — the ClientConnection's own channelActive
     * handler will set the channel field when the channel becomes active.
     * This ensures isConnected() returns false until channelActive fires,
     * so any pending callbacks are properly queued instead of executing immediately.
     */
    public static Object createUnbound(PacketFlowDirection receiving) throws Exception {
        ensureResolved();
        Object flow = Enum.valueOf((Class<Enum>) packetFlowClass, receiving.name());
        return connectionCtor.newInstance(flow);
    }

    private static synchronized void ensureResolved() throws Exception {
        if (resolved) return;
        connectionClass = FabricReflect.mcClass("net.minecraft.network.ClientConnection");
        packetFlowClass = FabricReflect.mcClass("net.minecraft.network.NetworkSide");
        connectionCtor = connectionClass.getConstructor(packetFlowClass);
        channelField = FabricReflect.mcField(connectionClass, "channel");
        channelField.setAccessible(true);
        addressField = FabricReflect.mcField(connectionClass, "address");
        addressField.setAccessible(true);
        resolved = true;
    }

    /** Direction enum mirroring NetworkSide (PacketFlow) without compile-time MC dependency. */
    public enum PacketFlowDirection {
        CLIENTBOUND,
        SERVERBOUND
    }

    private ConnectionFactory() {}
}