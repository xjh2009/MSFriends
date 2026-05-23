package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into {@link IntegratedServer} lifecycle to trigger P2P scope
 * changes reactively.
 *
 * <p>In 1.19.2 Yarn: enforceSecureProfile → shouldEnforceSecureProfile,
 * stopServer → shutdown, IntegratedServer is in net.minecraft.server.integrated.
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

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void msf$onServerStop(CallbackInfo ci) {
        if (!((Object) this instanceof IntegratedServer)) return;

        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;

        // Reset scope on server stop
        client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.OFF);

        // Tear down P2P — mirrors 26.1.2's onHostServerStopping
        if (client.p2p() != null) {
            client.p2p().onHostServerStopping();
        }

        // Force presence update so friends see us go offline/stop hosting
        if (client.social() != null) {
            client.social().getPresenceHandler().tryUpdatePresence();
        }
    }
}
