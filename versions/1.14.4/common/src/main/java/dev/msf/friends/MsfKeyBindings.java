package dev.msf.friends;

import net.minecraft.client.util.InputUtil;
import net.minecraft.client.options.KeyBinding;

import java.util.Map;
import java.util.Set;

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

    /** Register our category into KeyBinding's private static maps to prevent NPE. */
    public static void registerCategory() {
        try {
            Map<String, Integer> orderMap = KeyBinding.categoryOrderMap;
            if (!orderMap.containsKey(CATEGORY)) {
                orderMap.put(CATEGORY, orderMap.size() + 1);
            }
            Set<String> categories = KeyBinding.keyCategories;
            if (!categories.contains(CATEGORY)) {
                categories.add(CATEGORY);
            }
        } catch (Exception e) {
            // Fields may not be accessible — fallback for 1.14.4
        }
    }

    private MsfKeyBindings() {}
}
