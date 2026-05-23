package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the P2P guest-connection flag whenever the client disconnects.
 *
 * <p>Mirrors 26.2's injection into Minecraft.clearClientLevel().
 * In 1.16.5 Yarn, the equivalent disconnect path is:
 * <ul>
 *   <li>{@code MinecraftClient.disconnect()} — called when leaving a world</li>
 *   <li>{@code MinecraftClient.stop()} — called during full client shutdown</li>
 * </ul>
 * We inject into both to ensure the flag is cleared in all cases.
 */
@Mixin(MinecraftClient.class)
public abstract class GuestConnectionMixin {

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void msf$clearGuestFlagOnDisconnect(CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client != null && client.bridge() != null) {
            client.bridge().setConnectedViaP2P(false);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void msf$clearGuestFlagOnStop(CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client != null && client.bridge() != null) {
            client.bridge().setConnectedViaP2P(false);
        }
    }
}