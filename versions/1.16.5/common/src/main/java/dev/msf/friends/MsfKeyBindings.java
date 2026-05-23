package dev.msf.friends;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Holds all key bindings registered by MSF Friends. */
public final class MsfKeyBindings {

    /** Key binding category shown in the Controls screen. */
    public static final String CATEGORY = "key.categories.msf_friends.category";

    /** Default key: O — opens the friends screen while in-game. */
    public static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends",
            InputUtil.Type.KEYSYM,
            79, // GLFW_KEY_O
            CATEGORY
    );

    private MsfKeyBindings() {}
}