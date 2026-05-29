package dev.msf.friends.bridge;

import org.apache.logging.log4j.Logger;
import dev.msf.friends.MsfFriendsConstants;

import java.util.UUID;

/**
 * Client-side P2P connection bridge for MC 1.8.9.
 */
public final class ConnectionBridge {
    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;

    private ConnectionBridge() {}

    /**
     * Called on the main client thread when the P2P connection is ready.
     */
    public static void joinHost(io.netty.channel.Channel rtcChannel, String guestName, UUID guestId) throws Exception {
        Object connection = ConnectionFactory.create(rtcChannel, null);
        setupClientHandler(connection, guestName, guestId);
        LOGGER.info("[bridge] joinHost: connected to {} ({})", guestName, guestId);
    }

    private static void setupClientHandler(Object connection, String hostName, UUID hostId) throws Exception {
        LOGGER.info("[bridge] Setting up client handler for host {}", hostName);
    }
}
