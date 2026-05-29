package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the P2P guest-connection flag whenever the client disconnects.
 * 1.13.2: loadWorld(null) is the disconnect path on Minecraft.
 */
@Mixin(Minecraft.class)
public abstract class GuestConnectionMixin {

    @Inject(method = "loadWorld(Lnet/minecraft/client/world/ClientWorld;)V", at = @At("HEAD"), require = 0)
    private void msf$clearGuestFlag(net.minecraft.client.world.ClientWorld worldIn, CallbackInfo ci) {
        MsfFriendsBoot client = MsfFriendsBoot.get();
        if (client != null && client.bridge() != null) {
            client.bridge().setConnectedViaP2P(false);
        }
    }
}
