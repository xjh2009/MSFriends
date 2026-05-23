package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into MinecraftServer lifecycle to trigger P2P scope changes.
 *
 * 1.20.1 Yarn: enforceSecureProfile → shouldEnforceSecureProfile,
 * stopServer → stop(boolean).
 */
@Mixin(MinecraftServer.class)
public abstract class IntegratedServerMixin {

    @Inject(method = "shouldEnforceSecureProfile", at = @At("HEAD"), cancellable = true, require = 0)
    private void msf$enforceSecureProfileForOnlineScope(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof IntegratedServer)) return;

        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;

        if (client.bridge().multiplayerScope() == MinecraftBridge.MultiplayerScope.ONLINE) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void msf$onServerStop(boolean suppressLog, CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;

        client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.OFF);

        if (client.p2p() != null) {
            client.p2p().onHostServerStopping();
        }

        if (client.social() != null) {
            client.social().getPresenceHandler().tryUpdatePresence();
        }
    }
}
