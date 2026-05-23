package dev.msf.friends.bridge;

import dev.msf.friends.screen.FriendToast;
import dev.msf.friends.util.Logging;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

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
        switch (type) {
            case "friend.added":
            case "friend.request_accepted":
            case "friend.request_received":
            case "friend.request_sent":
            case "friend.invite_from":
            case "friend.invite_expired":
            case "friend.join_request":
            case "friend.join_expired":
            case "friend.host_invite_expired":
            case "friend.action.rate_limited":
            case "friend.action.unknown_profile":
            case "friend.action.forbidden":
            case "friend.action.unavailable":
            case "friend.action.failed":
            case "friend.status.online":
            case "friend.status.playing_offline":
            case "friend.status.hosting":
            case "friend.status.realms":
            case "friend.status.server":
                return new TranslatableText("toast.msf_friends." + type + ".title");
            default:
                return new TranslatableText("toast.msf_friends.default.title");
        }
    }

    private static Text getDescriptionForType(String type, String name) {
        switch (type) {
            case "friend.added":
            case "friend.request_accepted":
            case "friend.request_received":
            case "friend.request_sent":
            case "friend.invite_from":
            case "friend.invite_expired":
            case "friend.join_request":
            case "friend.join_expired":
            case "friend.host_invite_expired":
            case "friend.status.online":
            case "friend.status.playing_offline":
            case "friend.status.hosting":
            case "friend.status.realms":
            case "friend.status.server":
                return new TranslatableText("toast.msf_friends." + type + ".description", name);
            case "friend.action.rate_limited":
            case "friend.action.unknown_profile":
            case "friend.action.forbidden":
            case "friend.action.unavailable":
            case "friend.action.failed":
                return new TranslatableText("toast.msf_friends." + type + ".description");
            default:
                return new TranslatableText("toast.msf_friends.default.description", name);
        }
    }

    private ToastBridge() {}
}