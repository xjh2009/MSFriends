package dev.msf.friends.mixin;

import dev.msf.friends.MsfKeyBindings;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles the MSF Friends key binding each game tick for MC 1.13.2.
 * When the player presses the bound key (default: O) while no screen is open,
 * the friends screen is opened.
 */
@Mixin(Minecraft.class)
public abstract class KeyBindingMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void msf$handleFriendsKey(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.currentScreen == null) {
            while (MsfKeyBindings.OPEN_FRIENDS.isPressed()) {
                mc.displayGuiScreen(new FriendsScreen(null));
            }
        }
    }
}
