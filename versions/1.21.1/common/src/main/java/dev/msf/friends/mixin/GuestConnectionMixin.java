package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the P2P guest-connection flag whenever the client leaves a level.
 * This ensures that if the player later joins a plain LAN game the window
 * title is not incorrectly patched to show "在线".
 */
@Mixin(Minecraft.class)
public abstract class GuestConnectionMixin {

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void msf$clearGuestFlag(Screen screen, CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client != null && client.bridge() != null) {
            client.bridge().setConnectedViaP2P(false);
        }
    }
}
