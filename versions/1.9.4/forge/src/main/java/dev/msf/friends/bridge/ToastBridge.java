package dev.msf.friends.bridge;

import dev.msf.friends.MsfFriendsConstants;
import org.apache.logging.log4j.Logger;

/**
 * Toast/notification bridge for MC 1.9.4.
 * MC 1.9.4 does not have the toast system — notifications via chat.
 */
public final class ToastBridge {
    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;

    private ToastBridge() {}

    public static void showNotification(String message) {
        try {
            HeadlessMinecraftBridge bridge = new HeadlessMinecraftBridge();
            bridge.showNotification(message);
        } catch (Exception e) {
            LOGGER.debug("[bridge] Could not show notification: {}", e.getMessage());
        }
    }

    public static void showFriendNotification(String type, String playerName) {
        showNotification(type + ": " + playerName);
    }
}
