package dev.msf.friends.util;

import org.apache.logging.log4j.Logger;

/**
 * PCL launcher detection. Java 8 compatible stub.
 */
public final class PclDetector {
    private static final Logger LOGGER = Logging.get();

    private PclDetector() {}

    public static void checkAndOpenIfPCL() {
        try {
            // PCL (Plain Craft Launcher) detection logic
            String launcherPath = System.getProperty("minecraft.launcher.brand");
            if (launcherPath != null && launcherPath.toLowerCase().contains("pcl")) {
                LOGGER.info("[pcl] PCL launcher detected");
            }
        } catch (Throwable t) {
            LOGGER.debug("[pcl] detection failed", t);
        }
    }
}
