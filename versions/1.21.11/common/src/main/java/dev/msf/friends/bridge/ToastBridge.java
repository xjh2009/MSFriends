package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

public final class ToastBridge {
    private static final Logger LOGGER = Logging.get();

    public static void showToast(String type, String name, @Nullable UUID profileId) {
        LOGGER.info("[toast-bridge] toast: type={}, name={}, profileId={}", type, name, profileId);
    }

    private ToastBridge() {}
}
