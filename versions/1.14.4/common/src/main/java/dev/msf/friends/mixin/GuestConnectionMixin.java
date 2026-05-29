package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the P2P guest-connection flag whenever the client leaves a level.
 */
@Mixin(MinecraftClient.class)
public abstract class GuestConnectionMixin {

    @Inject(method = "disconnect()V", at = @At("HEAD"), require = 0)
    private void msf$clearGuestFlag(CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client != null && client.bridge() != null) {
            client.bridge().setConnectedViaP2P(false);
        }
    }
}
