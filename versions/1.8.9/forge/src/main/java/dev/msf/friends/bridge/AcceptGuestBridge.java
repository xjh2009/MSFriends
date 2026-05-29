package dev.msf.friends.bridge;

import org.apache.logging.log4j.Logger;
import dev.msf.friends.MsfFriendsConstants;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Server-side bridge that accepts a P2P guest connection for MC 1.8.9.
 */
public final class AcceptGuestBridge {
    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;

    private AcceptGuestBridge() {}

    /**
     * Accept a P2P guest connection on the integrated server.
     */
    public static void acceptGuest(io.netty.channel.Channel rtcChannel, UUID guestProfileId) throws Exception {
        Object connection = ConnectionFactory.create(rtcChannel, null);
        setupServerHandshake(connection, guestProfileId);
        addToServerConnections(connection);
        LOGGER.info("[bridge] Accepted P2P guest connection for {}", guestProfileId);
    }

    private static void setupServerHandshake(Object connection, UUID guestProfileId) throws Exception {
        Object server = ForgeReflect.getIntegratedServer();
        if (server == null) {
            LOGGER.warn("[bridge] Cannot setup server handshake: integrated server not available");
            return;
        }
        try {
            Class<?> handshakeClass = ForgeReflect.mcClass("net.minecraft.server.network.NetHandlerHandshakeServer");
            Class<?> nmClass = ForgeReflect.mcClass("net.minecraft.network.NetworkManager");
            Constructor<?> ctor = handshakeClass.getConstructor(server.getClass(), nmClass);
            Object listener = ctor.newInstance(server, connection);
            Method setListener = nmClass.getMethod("setNetHandler",
                    ForgeReflect.mcClass("net.minecraft.network.INetHandler"));
            setListener.invoke(connection, listener);
        } catch (Exception e) {
            LOGGER.warn("[bridge] Could not setup server handshake listener: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void addToServerConnections(Object connection) throws Exception {
        Object server = ForgeReflect.getIntegratedServer();
        if (server == null) return;
        try {
            Class<?> nsClass = ForgeReflect.mcClass("net.minecraft.network.NetworkSystem");
            Method getConnections = nsClass.getMethod("getNetworkManagers");
            Method getNS = server.getClass().getMethod("getNetworkSystem");
            Object ns = getNS.invoke(server);
            if (ns != null) {
                List<Object> connections = (List<Object>) getConnections.invoke(ns);
                connections.add(connection);
            }
        } catch (Exception e) {
            LOGGER.warn("[bridge] Could not add to server connections: {}", e.getMessage());
        }
    }
}
