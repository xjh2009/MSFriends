package dev.msf.friends.mixin;

import dev.msf.friends.MsfKeyBindings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Appends the MSF Friends key bindings to the vanilla {@code GameSettings.keyBindings}
 * array so they appear in the in-game Controls screen. 1.13.2 version.
 */
@Mixin(GameSettings.class)
public abstract class OptionsMixin {

    @Mutable
    @Shadow
    public KeyBinding[] keyBindings;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void msf$registerKeyBindings(CallbackInfo ci) {
        KeyBinding[] extended = Arrays.copyOf(this.keyBindings, this.keyBindings.length + 1);
        extended[this.keyBindings.length] = MsfKeyBindings.OPEN_FRIENDS;
        this.keyBindings = extended;
    }
}
