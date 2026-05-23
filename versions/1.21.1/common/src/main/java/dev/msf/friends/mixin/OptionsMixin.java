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
    @Shadow(remap = false)
    public KeyMapping[] field_1839;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void msf$registerKeyBindings(CallbackInfo ci) {
        KeyMapping[] extended = Arrays.copyOf(this.field_1839, this.field_1839.length + 1);
        extended[this.field_1839.length] = MsfKeyBindings.OPEN_FRIENDS;
        this.field_1839 = extended;
    }
}
