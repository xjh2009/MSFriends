package dev.msf.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Key bindings for MSF Friends on MC 1.10.
 *
 * <p>Registration uses {@link ClientRegistry#registerKeyBinding} — Forge's
 * client-side helper.  Press handling is done via {@code TickEvent.ClientTickEvent}
 * in the entry point rather than Mixin (Forge 1.10 does not support Mixin easily).
 */
@SideOnly(Side.CLIENT)
public final class MsfKeyBindings {

    /** Category shown in the Controls screen. */
    public static final String CATEGORY = "key.categories.msf_friends.category";

    /** Opens the friends screen. Default: O. */
    public static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends",
            Keyboard.KEY_O,
            CATEGORY
    );

    private MsfKeyBindings() {}

    /** Call once during {@code FMLPreInitializationEvent} on the client side. */
    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_FRIENDS);
    }
}
