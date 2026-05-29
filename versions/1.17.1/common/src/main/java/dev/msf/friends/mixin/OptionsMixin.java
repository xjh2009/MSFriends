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
 *
 * <p>1.17.1 uses field name "keysAll" (same as 1.16.5, renamed to "allKeys" in 1.18.2).
 * Also registers the custom category in KeyBinding.CATEGORY_ORDER_MAP to prevent NPE.
 * Category registration is handled by MsfKeyBindings.registerCategory().
 */
@Mixin(GameOptions.class)
public abstract class OptionsMixin {

    @Mutable
    @Shadow
    public KeyBinding[] keysAll;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void msf$registerKeyBindings(CallbackInfo ci) {
        MsfKeyBindings.registerCategory();

        KeyBinding[] extended = Arrays.copyOf(this.keysAll, this.keysAll.length + 1);
        extended[this.keysAll.length] = MsfKeyBindings.OPEN_FRIENDS;
        this.keysAll = extended;
    }
}
