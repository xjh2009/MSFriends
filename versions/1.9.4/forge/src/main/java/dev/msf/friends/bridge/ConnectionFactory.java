package dev.msf.friends.bridge;

import org.apache.logging.log4j.Logger;
import dev.msf.friends.MsfFriendsConstants;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Creates NetworkManager objects for MC 1.9.4 via reflection.
 */
public final class ConnectionFactory {
    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;

    private ConnectionFactory() {}

    /**
     * Create a new NetworkManager bound to the given channel.
     */
    public static Object create(io.netty.channel.Channel channel, Object packetFlow) throws Exception {
        Class<?> nmClass = ForgeReflect.mcClass("net.minecraft.network.NetworkManager");

        // MC 1.9.4: NetworkManager has a private constructor taking EnumConnectionState
        Class<?> ecsClass = ForgeReflect.mcClass("net.minecraft.network.EnumConnectionState");
        Object playState = ecsClass.getField("PLAY").get(null);

        Constructor<?> ctor = nmClass.getDeclaredConstructor(ecsClass);
        ctor.setAccessible(true);
        Object connection = ctor.newInstance(playState);

        // Set channel field
        try {
            Field channelField = nmClass.getDeclaredField("channel");
            channelField.setAccessible(true);
            channelField.set(connection, channel);
        } catch (NoSuchFieldException e) {
            LOGGER.warn("[bridge] Could not set channel field on NetworkManager");
        }

        // Set address field
        try {
            Field addressField = nmClass.getDeclaredField("socketAddress");
            addressField.setAccessible(true);
            addressField.set(connection, new java.net.InetSocketAddress("127.0.0.1", 0));
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[bridge] Could not set address field on NetworkManager (non-fatal)");
        }

        return connection;
    }
}
