package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class GuestConnectionMixin {
    @Inject(method = "clearLevel", at = @At("HEAD"))
    private void msf$clearGuestFlag(CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client != null && client.bridge() != null)
            client.bridge().setConnectedViaP2P(false);
    }
}
