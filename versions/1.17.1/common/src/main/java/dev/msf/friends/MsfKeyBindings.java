package dev.msf.friends;

import net.minecraft.client.util.InputUtil;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;

/** Holds all key bindings registered by MSF Friends. */
public final class MsfKeyBindings {

    public static final String CATEGORY = "key.categories.msf_friends.category";

    /** Default key: O — opens the friends screen while in-game. */
    public static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends",
            InputUtil.Type.KEYSYM,
            79, // GLFW_KEY_O
            CATEGORY
    );

    /** Register our category into KeyBinding's private static maps to prevent NPE.
     *  Fields are made accessible via msf-friends.accesswidener (referenced in fabric.mod.json). */
    public static void registerCategory() {
        Map<String, Integer> orderMap = KeyBinding.CATEGORY_ORDER_MAP;
        if (!orderMap.containsKey(CATEGORY)) {
            orderMap.put(CATEGORY, orderMap.size() + 1);
        }
        Set<String> categories = KeyBinding.KEY_CATEGORIES;
        if (!categories.contains(CATEGORY)) {
            categories.add(CATEGORY);
        }
    }

    private MsfKeyBindings() {}
}
