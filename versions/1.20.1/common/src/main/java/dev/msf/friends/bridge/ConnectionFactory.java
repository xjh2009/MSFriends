package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Equivalent of 26.2's {@code Connection.fromChannel(Channel, PacketFlow, BandwidthLogger)}.
 * Adapted for MC 1.20.1 Yarn names.
 *
 * <p>Creates a Connection bound to an existing channel, with the channel and
 * address fields already set. The caller is responsible for configuring the
 * pipeline and setting up protocol listeners afterward.
 *
 * <p>In 1.20.1 there is no BandwidthDebugMonitor, so the 3-arg fromChannel
 * overload is omitted (2-arg only). Otherwise logic matches 26.1.2 exactly.
 */
public final class ConnectionFactory {
    private static final Logger LOGGER = Logging.get();

    // Cached reflection handles (resolved once on first use)
    private static volatile boolean resolved;
    private static Class<?> connectionClass;
    private static Class<?> networkSideClass;
    private static Constructor<?> connectionCtor;
    private static Field channelField;
    private static Field addressField;

    /**
     * Packet flow direction, mirroring MC's NetworkSide enum.
     */
    public enum PacketFlowDirection {
        CLIENTBOUND,
        SERVERBOUND
    }

    /**
     * Create a new Connection bound to the given channel.
     * Sets both channel and address fields immediately.
     * Use this when the channel is already registered and active.
     */
    public static Object fromChannel(Channel channel, PacketFlowDirection receiving) throws Exception {
        ensureResolved();

        Object flow = Enum.valueOf((Class<Enum>) networkSideClass, receiving.name());
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

        Object flow = Enum.valueOf((Class<Enum>) networkSideClass, receiving.name());
        return connectionCtor.newInstance(flow);
    }

    private static synchronized void ensureResolved() throws Exception {
        if (resolved) return;
        synchronized (ConnectionFactory.class) {
            if (resolved) return;

            connectionClass = FabricReflect.mcClass("net.minecraft.network.ClientConnection");
            networkSideClass = FabricReflect.mcClass("net.minecraft.network.NetworkSide");
            connectionCtor = connectionClass.getDeclaredConstructor(networkSideClass);
            connectionCtor.setAccessible(true);
            channelField = FabricReflect.mcField(connectionClass, "channel");
            channelField.setAccessible(true);
            addressField = FabricReflect.mcField(connectionClass, "address");
            addressField.setAccessible(true);

            resolved = true;
        }
    }

    private ConnectionFactory() {}
}
