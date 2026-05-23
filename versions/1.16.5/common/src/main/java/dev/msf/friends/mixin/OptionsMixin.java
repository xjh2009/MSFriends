package dev.msf.friends.mixin;

import dev.msf.friends.MsfKeyBindings;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Appends the MSF Friends key bindings to the vanilla {@code GameOptions.keysAll}
 * array so they appear in the in-game Controls screen.
 */
@Mixin(GameOptions.class)
public abstract class OptionsMixin {

    @Mutable
    @Shadow
    public KeyBinding[] keysAll;

    @Inject(method = "load", at = @At("RETURN"))
    private void msf$registerKeyBindings(CallbackInfo ci) {
        // Avoid double-registration
        for (KeyBinding kb : this.keysAll) {
            if (kb == MsfKeyBindings.OPEN_FRIENDS) return;
        }
        KeyBinding[] extended = Arrays.copyOf(this.keysAll, this.keysAll.length + 1);
        extended[this.keysAll.length] = MsfKeyBindings.OPEN_FRIENDS;
        this.keysAll = extended;
    }
}
