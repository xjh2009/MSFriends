package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into server lifecycle for P2P scope changes.
 *
 * <p>Mirrors 26.2's IntegratedServer lifecycle callbacks:
 * <ul>
 *   <li>{@code shutdown()} — When the server stops, reset scope to OFF and tear down P2P.</li>
 *   <li>{@code enforceSecureProfile()} — Not present in 1.16.5 (no secure chat), so we use
 *       a simpler check: when ONLINE scope is active, the server should enforce secure profiles.
 *       Since 1.16.5 doesn't have this concept, we skip this injection entirely.</li>
 * </ul>
 *
 * <p>In 1.16.5 Yarn: MinecraftServer.shutdown() is the shutdown method.
 * The target class is MinecraftServer (not IntegratedServer) because
 * IntegratedServer doesn't override shutdown().
 */
@Mixin(MinecraftServer.class)
public abstract class IntegratedServerMixin {

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void msf$onServerShutdown(CallbackInfo ci) {
        if (!((Object) this instanceof IntegratedServer)) return;
        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;

        // Reset scope to OFF — server is shutting down
        client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.OFF);

        // Notify P2P manager to tear down hosting state
        if (client.p2p() != null) {
            client.p2p().onHostServerStopping();
        }

        // Force presence update so friends see us go offline/stop hosting
        if (client.social() != null) {
            client.social().getPresenceHandler().tryUpdatePresence();
        }
    }
}