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
 * 1.20.1 Yarn: Minecraft → MinecraftClient.
 */
@Mixin(MinecraftClient.class)
public abstract class KeyBindingMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void msf$handleFriendsKey(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.currentScreen == null) {
            while (MsfKeyBindings.OPEN_FRIENDS.wasPressed()) {
                mc.setScreen(new FriendsScreen(null));
            }
        }
    }
}
