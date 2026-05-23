package dev.msf.friends.mixin;

import dev.msf.friends.MsfKeyBindings;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles the MSF Friends key binding each tick.
 * In 1.16.5, the main tick+render is in render(boolean).
 * We inject at the end of render to check key bindings.
 */
@Mixin(MinecraftClient.class)
public abstract class KeyBindingMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void msf$handleFriendsKey(boolean tick, CallbackInfo ci) {
        if (!tick) return;
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.currentScreen == null) {
            while (MsfKeyBindings.OPEN_FRIENDS.wasPressed()) {
                mc.openScreen(new FriendsScreen(null));
            }
        }
    }
}