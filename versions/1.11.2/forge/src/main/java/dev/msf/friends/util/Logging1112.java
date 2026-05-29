package dev.msf.friends;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Java-8-compatible logging wrapper for MC 1.11.2. */
public final class Logging1112 {
    private static final Logger LOG = LogManager.getLogger("MSF/Friends");

    private Logging1112() {}

    public static Logger get() { return LOG; }
}
