package dev.msf.friends.bridge;

import dev.msf.friends.screen.FriendToast;
import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Bridges toast notifications into MC's toast system using {@link FriendToast},
 * which renders the triggering player's skin head alongside the notification text.
 *
 * <p>Must be called on the render/client thread.
 *
 * <p>Toast type mapping matches 26.1.2 exactly — every type produces a
 * translatable title and description key, preserving the same categorization.
 */
public final class ToastBridge {
    private static final Logger LOGGER = Logging.get();

    /**
     * Show an in-game toast notification for a friend event.
     * The toast displays the player's skin head (loaded from UUID) next to the text.
     * Must be called on the Render thread.
     */
    public static void showToast(String type, String name, @Nullable UUID profileId) {
        try {
            net.minecraft.text.Text title = getTitleForType(type);
            net.minecraft.text.Text description = getDescriptionForType(type, name);
            FriendToast.show(title, description, profileId);
        } catch (Throwable t) {
            LOGGER.debug("[toast-bridge] failed to show toast: {}", t.getMessage());
        }
    }

    private static net.minecraft.text.Text getTitleForType(String type) {
        return switch (type) {
            case "friend.added",
                 "friend.request_accepted",
                 "friend.request_received",
                 "friend.request_sent",
                 "friend.invite_from",
                 "friend.invite_expired",
                 "friend.join_request",
                 "friend.join_expired",
                 "friend.host_invite_expired",
                 "friend.action.rate_limited",
                 "friend.action.unknown_profile",
                 "friend.action.forbidden",
                 "friend.action.unavailable",
                 "friend.action.failed",
                 "friend.status.online",
                 "friend.status.playing_offline",
                 "friend.status.hosting",
                 "friend.status.realms",
                 "friend.status.server" -> net.minecraft.text.Text.translatable("toast.msf_friends." + type + ".title");
            default -> net.minecraft.text.Text.translatable("toast.msf_friends.default.title");
        };
    }

    private static net.minecraft.text.Text getDescriptionForType(String type, String name) {
        return switch (type) {
            case "friend.added",
                 "friend.request_accepted",
                 "friend.request_received",
                 "friend.request_sent",
                 "friend.invite_from",
                 "friend.invite_expired",
                 "friend.join_request",
                 "friend.join_expired",
                 "friend.host_invite_expired",
                 "friend.status.online",
                 "friend.status.playing_offline",
                 "friend.status.hosting",
                 "friend.status.realms",
                 "friend.status.server" -> net.minecraft.text.Text.translatable("toast.msf_friends." + type + ".description", name);
            case "friend.action.rate_limited",
                 "friend.action.unknown_profile",
                 "friend.action.forbidden",
                 "friend.action.unavailable",
                 "friend.action.failed" -> net.minecraft.text.Text.translatable("toast.msf_friends." + type + ".description");
            default -> net.minecraft.text.Text.translatable("toast.msf_friends.default.description", name);
        };
    }

    private ToastBridge() {}
}
