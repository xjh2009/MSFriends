package dev.msf.friends.mixin;

import dev.msf.friends.MsfKeyBindings;
import dev.msf.friends.bridge.FabricReflect;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Appends the MSF Friends key bindings to the vanilla {@code Options.keyMappings}
 * array so they appear in the in-game Controls screen.
 *
 * Uses FabricReflect instead of @Accessor because the Mixin AP fails to
 * generate refmap entries for @Accessor fields when using official Mojang
 * mappings with Fabric Loom, causing runtime NoSuchFieldError crashes.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void msf$registerKeyBindings(Minecraft mc, java.io.File gameDir, CallbackInfo ci) {
        try {
            Field keyMappingsField = FabricReflect.mcField(Options.class, "keyMappings");
            keyMappingsField.setAccessible(true);
            KeyMapping[] current = (KeyMapping[]) keyMappingsField.get(this);
            KeyMapping[] extended = Arrays.copyOf(current, current.length + 1);
            extended[current.length] = MsfKeyBindings.OPEN_FRIENDS;
            keyMappingsField.set(this, extended);
        } catch (Exception e) {
            System.err.println("[msf-friends] Failed to inject key binding: " + e.getMessage());
        }
    }
}
