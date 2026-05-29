package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@link IntegratedServer} lifecycle to trigger P2P scope changes.
 */
@Mixin(MinecraftServer.class)
public abstract class IntegratedServerMixin {

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void msf$onServerStop(CallbackInfo ci) {
        if (!((Object) this instanceof IntegratedServer)) return;

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
