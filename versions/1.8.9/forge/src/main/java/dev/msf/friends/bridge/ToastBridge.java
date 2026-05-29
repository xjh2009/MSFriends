package dev.msf.friends.bridge;

import dev.msf.friends.MsfFriendsConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.apache.logging.log4j.Logger;

/**
 * Toast/notification bridge for MC 1.8.9.
 * MC 1.8.9 does not have the toast system — notifications via chat.
 */
public final class ToastBridge {
    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;

    private ToastBridge() {}

    public static void showNotification(String message) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                ChatComponentText text = new ChatComponentText(
                        EnumChatFormatting.GREEN + "[MSF Friends] " + EnumChatFormatting.RESET + message);
                mc.thePlayer.addChatMessage(text);
            }
        } catch (Exception e) {
            LOGGER.debug("[bridge] Could not show notification: {}", e.getMessage());
        }
    }

    public static void showFriendNotification(String type, String playerName) {
        showNotification(type + ": " + playerName);
    }
}
