package dev.msf.friends.mixin;

import dev.msf.friends.MsfKeyBindings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class KeyBindingMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void msf$handleFriendsKey(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.screen == null) {
            while (MsfKeyBindings.OPEN_FRIENDS.consumeClick()) {
                // Friends screen not yet ported to 1.21.11
            }
        }
    }
}
