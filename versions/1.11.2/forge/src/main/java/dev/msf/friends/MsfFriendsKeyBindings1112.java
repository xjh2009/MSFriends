package dev.msf.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * Key bindings for MSF Friends on MC 1.11.2.
 */
public final class MsfFriendsKeyBindings1112 {

    private static final Logger LOGGER = Logging1112.get();

    public static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends",
            Keyboard.KEY_F,
            "key.categories.msf_friends");

    private MsfFriendsKeyBindings1112() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_FRIENDS);
        LOGGER.info("[keys] Registered key bindings (F for friends screen)");
    }
}
