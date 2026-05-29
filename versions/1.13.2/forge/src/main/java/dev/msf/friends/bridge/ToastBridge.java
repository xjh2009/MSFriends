package dev.msf.friends.bridge;

import dev.msf.friends.screen.FriendToast;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.ITextComponent;
import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Bridges toast notifications for MC 1.13.2 Forge.
 *
 * <p>1.13.2 MCP: uses {@link TextComponentTranslation} (equivalent of
 * {@code TranslatableText} in yarn / {@code TranslatableComponent} in Mojmap).
 * The {@link FriendToast#show} method accepts {@link ITextComponent}.
 */
public final class ToastBridge {
    private static final Logger LOGGER = Logging.get();

    public static void showToast(String type, String name, @Nullable UUID profileId) {
        try {
            ITextComponent title = getTitleForType(type);
            ITextComponent description = getDescriptionForType(type, name);
            FriendToast.show(title, description, profileId);
        } catch (Throwable t) {
            LOGGER.debug("[toast-bridge] failed to show toast: {}", t.getMessage());
        }
    }

    private static ITextComponent getTitleForType(String type) {
        return new TextComponentTranslation("toast.msf_friends." + type + ".title");
    }

    private static ITextComponent getDescriptionForType(String type, String name) {
        switch (type) {
            case "friend.action.rate_limited":
            case "friend.action.unknown_profile":
            case "friend.action.forbidden":
            case "friend.action.unavailable":
            case "friend.action.failed":
                return new TextComponentTranslation("toast.msf_friends." + type + ".description");
            default:
                return new TextComponentTranslation("toast.msf_friends." + type + ".description", name);
        }
    }

    private ToastBridge() {}
}
