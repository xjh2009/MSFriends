package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Equivalent of 26.2's {@code Connection.fromChannel(Channel, PacketFlow, BandwidthLogger)}.
 *
 * <p>Creates a Connection bound to an existing channel, with the channel and
 * address fields already set. The caller is responsible for configuring the
 * pipeline and setting up protocol listeners afterward.
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
     * Create a new Connection bound to the given channel.
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
     * Create a new Connection WITHOUT setting the channel field.
     * Use this inside a ChannelInitializer — the Connection's own channelActive
     * handler will set the channel field when the channel becomes active.
     * This ensures isConnected() returns false until channelActive fires,
     * so runOnceConnected callbacks are properly queued instead of executing immediately.
     */
    public static Object createUnbound(PacketFlowDirection receiving) throws Exception {
        ensureResolved();
        Object flow = Enum.valueOf((Class<Enum>) packetFlowClass, receiving.name());
        return connectionCtor.newInstance(flow);
    }

    /**
     * Overload that also sets a BandwidthDebugMonitor on the connection (client-side use).
     */
    public static Object fromChannel(Channel channel, PacketFlowDirection receiving,
                                     @Nullable Object bandwidthLogger) throws Exception {
        Object connection = fromChannel(channel, receiving);

        if (bandwidthLogger != null) {
            try {
                Class<?> monitorClass = Class.forName("net.minecraft.network.BandwidthDebugMonitor");
                Class<?> loggerClass = Class.forName("net.minecraft.util.debugchart.LocalSampleLogger");
                Constructor<?> monitorCtor = monitorClass.getConstructor(loggerClass);
                Object monitor = monitorCtor.newInstance(bandwidthLogger);

                Field monitorField = connectionClass.getDeclaredField("bandwidthDebugMonitor");
                monitorField.setAccessible(true);
                monitorField.set(connection, monitor);
            } catch (NoSuchFieldException e) {
                LOGGER.debug("[connection-factory] no bandwidthDebugMonitor field (ok on server side)");
            }
        }

        return connection;
    }

    private static synchronized void ensureResolved() throws Exception {
        if (resolved) return;
        connectionClass = Class.forName("net.minecraft.network.Connection");
        packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        connectionCtor = connectionClass.getConstructor(packetFlowClass);
        channelField = connectionClass.getDeclaredField("channel");
        channelField.setAccessible(true);
        addressField = connectionClass.getDeclaredField("address");
        addressField.setAccessible(true);
        resolved = true;
    }

    /** Direction enum mirroring PacketFlow without compile-time MC dependency. */
    public enum PacketFlowDirection {
        CLIENTBOUND,
        SERVERBOUND
    }

    private ConnectionFactory() {}
}
