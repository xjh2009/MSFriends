package dev.msf.friends.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.msf.friends.MsfKeyBindings;

/**
 * Injects into {@code Minecraft.runTick()} to check if the friends
 * keybinding was pressed and open the friends screen.
 */
@Mixin(Minecraft.class)
public abstract class KeyBindingMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void msf$onTick(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;

        while (MsfKeyBindings.OPEN_FRIENDS.isPressed()) {
            // TODO: open FriendsScreen when the GUI layer is ported to 1.10
            // mc.displayGuiScreen(new FriendsScreen(mc.currentScreen));
        }
    }
}
