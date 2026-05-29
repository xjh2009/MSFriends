package dev.msf.friends;

import net.minecraft.client.settings.KeyBinding;

/**
 * Key bindings for MSF Friends mod on 1.14.4 Forge.
 * Uses raw GLFW key code (GLFW_KEY_O = 79) to avoid LWJGL dependency issues.
 */
public final class MsfKeyBindings {
    public static final String CATEGORY = "key.categories.msf_friends.category";

    // GLFW_KEY_O = 79
    public static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends",
            79,
            CATEGORY
    );

    private MsfKeyBindings() {}
}
