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
 * Appends the MSF Friends key bindings to the vanilla GameOptions.keysAll
 * array so they appear in the in-game Controls screen.
 *
 * 1.20.1 Yarn: Options → GameOptions, KeyMapping → KeyBinding.
 */
@Mixin(GameOptions.class)
public abstract class OptionsMixin {

    @Mutable
    @Shadow
    public KeyBinding[] allKeys;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void msf$registerKeyBindings(CallbackInfo ci) {
        KeyBinding[] extended = Arrays.copyOf(this.allKeys, this.allKeys.length + 1);
        extended[this.allKeys.length] = MsfKeyBindings.OPEN_FRIENDS;
        this.allKeys = extended;
    }
}
