package dev.msf.friends.mixin;

import dev.msf.friends.MsfKeyBindings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Appends the MSF Friends key bindings to the vanilla {@code Options.keyMappings}
 * array so they appear in the in-game Controls screen.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {

    @Mutable
    @Shadow
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void msf$registerKeyBindings(CallbackInfo ci) {
        KeyMapping[] extended = Arrays.copyOf(this.keyMappings, this.keyMappings.length + 1);
        extended[this.keyMappings.length] = MsfKeyBindings.OPEN_FRIENDS;
        this.keyMappings = extended;
    }
}
