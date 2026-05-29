package dev.msf.friends.bridge;

import dev.msf.friends.screen.FriendToast;
import dev.msf.friends.util.Logging;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Bridges toast notifications for MC 1.17.1.
 * Uses TranslatableText (1.17.1 API).
 */
public final class ToastBridge {
    private static final Logger LOGGER = Logging.get();

    public static void showToast(String type, String name, @Nullable UUID profileId) {
        try {
            Text title = getTitleForType(type);
            Text description = getDescriptionForType(type, name);
            FriendToast.show(title, description, profileId);
        } catch (Throwable t) {
            LOGGER.debug("[toast-bridge] failed to show toast: {}", t.getMessage());
        }
    }

    private static Text getTitleForType(String type) {
        return new TranslatableText("toast.msf_friends." + type + ".title");
    }

    private static Text getDescriptionForType(String type, String name) {
        switch (type) {
            case "friend.action.rate_limited":
            case "friend.action.unknown_profile":
            case "friend.action.forbidden":
            case "friend.action.unavailable":
            case "friend.action.failed":
                return new TranslatableText("toast.msf_friends." + type + ".description");
            default:
                return new TranslatableText("toast.msf_friends." + type + ".description", name);
        }
    }

    private ToastBridge() {}
}
