package dev.msf.friends.util;

import org.apache.logging.log4j.LogManager;

/**
 * Simple logging factory using Log4j2 (available in MC 1.12.2).
 * Returns Apache Log4j Logger instances instead of SLF4J.
 */
public final class Logging {
    private Logging() {}

    public static org.apache.logging.log4j.Logger get() {
        return LogManager.getLogger(MsfFriendsConstants.LOGGER_NAME);
    }

    public static org.apache.logging.log4j.Logger get(String name) {
        return LogManager.getLogger(name);
    }

    // Inline constant so we don't import from dev.msf.friends
    private static final class MsfFriendsConstants {
        static final String LOGGER_NAME = "MSF/Friends";
    }
}
