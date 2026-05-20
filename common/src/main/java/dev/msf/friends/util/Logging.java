package dev.msf.friends.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.msf.friends.MsfFriendsConstants;

/** Centralised logger factory. */
public final class Logging {
    private static final Logger LOGGER = LoggerFactory.getLogger(MsfFriendsConstants.LOGGER_NAME);

    public static Logger get() {
        return LOGGER;
    }

    private Logging() {}
}
