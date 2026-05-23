package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into {@link IntegratedServer} lifecycle to trigger P2P scope
 * changes reactively — replacing the old 1 Hz polling approach.
 *
 * <p>This mirrors 26.2's {@code IntegratedServer.onHostScopeChanged(scope)}
 * callback that was added to the game in that version. Since 26.1.2
 * doesn't have that callback, we inject into the server lifecycle
 * methods to achieve the same effect.
 *
 * <ul>
 *   <li>{@code stopServer()} — When the integrated server stops (player
 *       leaves the world), reset the scope to OFF and tear down P2P.</li>
 * </ul>
 *
 * <p>The publish-time scope activation is handled by
 * {@link ShareToLanScreenMixin} which fires when the user presses
 * "Start LAN World" on the screen.
 */
@Mixin(MinecraftServer.class)
public abstract class IntegratedServerMixin {

    /**
     * Mirrors 26.2's IntegratedServer.enforceSecureProfile(): ONLINE-published
     * rooms advertise secure chat so guests do not get the insecure-server toast.
     */
    @Inject(method = "enforceSecureProfile", at = @At("HEAD"), cancellable = true, require = 0)
    private void msf$enforceSecureProfileForOnlineScope(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof IntegratedServer)) return;

        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;

        if (client.bridge().multiplayerScope() == MinecraftBridge.MultiplayerScope.ONLINE) {
            cir.setReturnValue(true);
        }
    }

    /**
     * When the integrated server stops, reset P2P scope to OFF.
     * This ensures the signaling connection is torn down and
     * presence stops broadcasting "hosting" status.
     */
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void msf$onServerStop(CallbackInfo ci) {
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
