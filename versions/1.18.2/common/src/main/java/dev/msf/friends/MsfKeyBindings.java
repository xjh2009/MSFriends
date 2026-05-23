package dev.msf.friends;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Holds all key bindings registered by MSF Friends. */
public final class MsfKeyBindings {

    /** Key binding category shown in the Controls screen. */
    public static final String CATEGORY = "key.categories.msf_friends";

    /** Default key: O — opens the friends screen while in-game. */
    public static final KeyMapping OPEN_FRIENDS = new KeyMapping(
            "key.msf_friends.open_friends",
            InputConstants.Type.KEYSYM,
            79, // GLFW_KEY_O
            CATEGORY
    );

    private MsfKeyBindings() {}
}
