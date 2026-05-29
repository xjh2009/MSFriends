package dev.msf.friends;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Constants for the MSF Friends mod — MC 1.8.9 Forge version.
 * Fully self-contained (does not import from :common which targets Java 17).
 */
public final class MsfFriendsConstants {

    public static final String MOD_ID = "msf_friends";
    public static final String MOD_NAME = "MSF Friends";
    public static final Logger LOGGER = LogManager.getLogger("MSF/Friends");

    // Polling intervals
    public static final long FRIENDS_POLL_INTERVAL_MS = 15_000L;
    public static final long PRESENCE_POLL_INTERVAL_MS = 5_000L;

    // RPC / discovery
    public static final int DISCOVERY_PORT = 25566;
    public static final int RPC_PORT = 25567;

    private MsfFriendsConstants() {}

    public static void logLifecycle(String stage) {
        LOGGER.info("[{}] {}", MOD_ID, stage);
    }
}
