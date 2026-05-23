package dev.msf.friends.bridge;

import dev.msf.friends.screen.FriendToast;
import dev.msf.friends.util.Logging;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Bridges toast notifications into MC 1.19.2's toast system using {@link FriendToast}.
 *
 * <p>1.19.2 Yarn: uses Text.translatable() (same API), but toast rendering
 * may differ slightly. Must be called on the render/client thread.
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
                 "friend.status.server" -> Text.translatable("toast.msf_friends." + type + ".title");
            default -> Text.translatable("toast.msf_friends.default.title");
        };
    }

    private static Text getDescriptionForType(String type, String name) {
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
                 "friend.status.server" -> Text.translatable("toast.msf_friends." + type + ".description", name);
            case "friend.action.rate_limited",
                 "friend.action.unknown_profile",
                 "friend.action.forbidden",
                 "friend.action.unavailable",
                 "friend.action.failed" -> Text.translatable("toast.msf_friends." + type + ".description");
            default -> Text.translatable("toast.msf_friends.default.description", name);
        };
    }

    private ToastBridge() {}
}
