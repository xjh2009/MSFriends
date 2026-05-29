package dev.msf.friends.util;

import dev.msf.friends.MsfFriendsConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Logging {
    private static final Logger LOGGER = LogManager.getLogger(MsfFriendsConstants.LOGGER_NAME);
    public static Logger get() { return LOGGER; }
    private Logging() {}
}
