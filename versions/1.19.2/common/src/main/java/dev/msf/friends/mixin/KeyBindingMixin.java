package dev.msf.friends.mixin;

import dev.msf.friends.MsfKeyBindings;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles the MSF Friends key binding each game tick.
 * When the player presses the bound key (default: O) while no screen is open,
 * the friends screen is opened.
 */
@Mixin(MinecraftClient.class)
public abstract class KeyBindingMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void msf$handleFriendsKey(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        // Only open when not already in a screen
        if (mc.currentScreen == null) {
            while (MsfKeyBindings.OPEN_FRIENDS.wasPressed()) {
                System.out.println("[msf-friends] KeyBindingMixin: Opening friends screen");
                mc.setScreen(new FriendsScreen(null));
            }
        }
    }
}
