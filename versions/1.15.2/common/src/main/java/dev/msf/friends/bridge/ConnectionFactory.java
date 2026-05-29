package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Creates {@code Connection} objects bound to existing Netty channels via reflection.
 *
 * <p>1.14.4: Connection constructor takes {@code PacketFlow} and we set channel/address via reflection.
 */
public final class ConnectionFactory {
    private static final Logger LOGGER = Logging.get();

    private ConnectionFactory() {}

    public static Object create(io.netty.channel.Channel channel, Object packetFlow) throws Exception {
        Class<?> connectionClass = FabricReflect.mcClass("net.minecraft.network.Connection");
        Class<?> packetFlowClass = FabricReflect.mcClass("net.minecraft.network.protocol.PacketFlow");

        Constructor<?> ctor = connectionClass.getConstructor(packetFlowClass);
        Object connection = ctor.newInstance(packetFlow);

        try {
            Field channelField = FabricReflect.mcField(connectionClass, "channel");
            channelField.setAccessible(true);
            channelField.set(connection, channel);
        } catch (NoSuchFieldException e) {
            LOGGER.warn("[bridge] Could not set channel field on Connection");
        }

        try {
            Field addressField = FabricReflect.mcField(connectionClass, "address");
            addressField.setAccessible(true);
            addressField.set(connection, new java.net.InetSocketAddress("127.0.0.1", 0));
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[bridge] Could not set address field on Connection (non-fatal)");
        }

        return connection;
    }
}
