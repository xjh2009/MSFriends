package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the P2P guest-connection flag whenever the client leaves a level.
 */
@Mixin(MinecraftClient.class)
public abstract class GuestConnectionMixin {

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void msf$clearGuestFlag(CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client != null && client.bridge() != null) {
            client.bridge().setConnectedViaP2P(false);
        }
    }
}
