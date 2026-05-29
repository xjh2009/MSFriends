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
            // Use reflection to handle both Yarn (dev) and SRG (runtime) field names
            java.lang.reflect.Field orderField = findField(KeyBinding.class,
                "categoryOrderMap", "field_74516_a", "field_1656");
            if (orderField != null) {
                orderField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Integer> orderMap = (Map<String, Integer>) orderField.get(null);
                if (!orderMap.containsKey(CATEGORY)) {
                    orderMap.put(CATEGORY, orderMap.size() + 1);
                }
            }
            java.lang.reflect.Field catField = findField(KeyBinding.class,
                "keyCategories", "field_193627_d", "field_1652");
            if (catField != null) {
                catField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Set<String> categories = (Set<String>) catField.get(null);
                if (!categories.contains(CATEGORY)) {
                    categories.add(CATEGORY);
                }
            }
        } catch (Throwable e) {
            // Cannot register category — keybindings may show under misc
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try { return clazz.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private MsfKeyBindings() {}
}
