package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "Open to LAN" screen behaviour with a 26.2-style
 * three-state scope model (Off / LAN / Online).
 *
 * <p>A toggle button is added next to the port field. The vanilla "Start"
 * button's {@code publishServer} call is redirected: when scope is Off,
 * the publish is skipped entirely (just close the screen); when LAN,
 * vanilla publish runs; when Online, vanilla publish runs AND the P2P
 * signaling system is activated.
 */
@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {

    @Shadow @Final private Screen lastScreen;
    @Shadow private int port;

    @Unique private MinecraftBridge.MultiplayerScope msf$scope = MinecraftBridge.MultiplayerScope.LAN;

    protected ShareToLanScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private static Component msf$label(MinecraftBridge.MultiplayerScope s) {
        return Component.translatable(switch (s) {
            case OFF    -> "options.msf_friends.multiplayer_scope.off";
            case LAN    -> "options.msf_friends.multiplayer_scope.lan";
            case ONLINE -> "options.msf_friends.multiplayer_scope.online";
        });
    }

    /* ---- add scope button next to port field ---- */

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addScopeButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;
        this.msf$scope = MinecraftBridge.MultiplayerScope.LAN;

        // Port field: (width/2-75, 160, 150, 20). Button goes right of it.
        int x = this.width / 2 - 75 + 150 + 4;
        this.addRenderableWidget(
            Button.builder(msf$label(this.msf$scope), btn -> {
                this.msf$scope = switch (this.msf$scope) {
                    case OFF    -> MinecraftBridge.MultiplayerScope.LAN;
                    case LAN    -> MinecraftBridge.MultiplayerScope.ONLINE;
                    case ONLINE -> MinecraftBridge.MultiplayerScope.OFF;
                };
                btn.setMessage(msf$label(this.msf$scope));
            }).bounds(x, 160, 60, 20).build()
        );
    }

    /* ---- redirect publishServer inside "Start LAN" button ---- */

    /**
     * Redirect the {@code IntegratedServer.publishServer()} call inside
     * {@code lambda$init$2}. When scope is Off we skip publishing entirely
     * and return false. When LAN or Online we let it through.
     */
    @Redirect(method = "lambda$init$2",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/server/IntegratedServer;publishServer(Lnet/minecraft/world/level/GameType;ZI)Z"))
    private boolean msf$redirectPublish(IntegratedServer server, GameType gameMode, boolean commands, int port) {
        if (this.msf$scope == MinecraftBridge.MultiplayerScope.OFF) {
            // Don't publish at all — user selected Off
            return false;
        }

        // Mirror 26.2 IntegratedServer.publishServer: prepare multiplayer state
        // and force the host's local connection to publish its chat session.
        if (this.minecraft != null) {
            this.minecraft.prepareForMultiplayer();
            var connection = this.minecraft.getConnection();
            if (connection != null) {
                connection.prepareKeyPair();
            }
        }

        // Normal LAN publish
        boolean ok = server.publishServer(gameMode, commands, port);

        if (ok && this.msf$scope == MinecraftBridge.MultiplayerScope.ONLINE) {
            // Activate P2P on top of LAN
            var client = MsfFriendsBoot.get();
            if (client != null && client.bridge() != null && client.p2p() != null) {
                client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.ONLINE);
                client.p2p().onHostScopeChanged(MinecraftBridge.MultiplayerScope.ONLINE);
                if (client.social() != null) {
                    client.social().getPresenceHandler().tryUpdatePresence();
                }
            }
        } else if (ok) {
            // LAN only — make sure scope is set to LAN (not ONLINE)
            var client = MsfFriendsBoot.get();
            if (client != null && client.bridge() != null) {
                client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.LAN);
            }
        }

        return ok;
    }
}
