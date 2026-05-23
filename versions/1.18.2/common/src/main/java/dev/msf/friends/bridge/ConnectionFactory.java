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
 *
 * <p>1.18.2 adaptation: BandwidthDebugMonitor may not exist, graceful fallback.
 */
public final class ConnectionFactory {
    private static final Logger LOGGER = Logging.get();

    private static volatile boolean resolved;
    private static Class<?> connectionClass;
    private static Class<?> packetFlowClass;
    private static Constructor<?> connectionCtor;
    private static Field channelField;
    private static Field addressField;

    /**
     * Create a new Connection bound to the given channel.
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
     * Use this inside a ChannelInitializer.
     */
    public static Object createUnbound(PacketFlowDirection receiving) throws Exception {
        ensureResolved();
        Object flow = Enum.valueOf((Class<Enum>) packetFlowClass, receiving.name());
        return connectionCtor.newInstance(flow);
    }

    /**
     * Overload that also sets a BandwidthDebugMonitor on the connection (client-side use).
     * In 1.18.2, BandwidthDebugMonitor may not exist — graceful degradation.
     */
    public static Object fromChannel(Channel channel, PacketFlowDirection receiving,
                                     @Nullable Object bandwidthLogger) throws Exception {
        Object connection = fromChannel(channel, receiving);

        if (bandwidthLogger != null) {
            try {
                Class<?> monitorClass = FabricReflect.mcClass("net.minecraft.network.BandwidthDebugMonitor");
                Class<?> loggerClass = FabricReflect.mcClass("net.minecraft.util.debugchart.LocalSampleLogger");
                Constructor<?> monitorCtor = monitorClass.getConstructor(loggerClass);
                Object monitor = monitorCtor.newInstance(bandwidthLogger);

                Field monitorField = FabricReflect.mcField(connectionClass, "bandwidthDebugMonitor");
                monitorField.setAccessible(true);
                monitorField.set(connection, monitor);
            } catch (Exception e) {
                LOGGER.debug("[connection-factory] no bandwidthDebugMonitor field (ok on 1.18.2/server side)");
            }
        }

        return connection;
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

    private ConnectionFactory() {}
}
