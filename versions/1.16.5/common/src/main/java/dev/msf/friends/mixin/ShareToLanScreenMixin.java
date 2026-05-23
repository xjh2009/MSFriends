package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.P2PManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.world.GameMode;
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
 * A toggle button is added next to the port field. The vanilla "Start"
 * button's {@code openToLan} call is redirected: when scope is Off,
 * the publish is skipped entirely; when LAN, vanilla publish runs;
 * when Online, vanilla publish runs AND the P2P signaling system is activated.
 */
@Mixin(OpenToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {

    @Shadow @Final private Screen parent;

    @Unique private MinecraftBridge.MultiplayerScope msf$scope = MinecraftBridge.MultiplayerScope.LAN;

    protected ShareToLanScreenMixin(Text title) {
        super(title);
    }

    @Unique
    private static Text msf$label(MinecraftBridge.MultiplayerScope s) {
        return new TranslatableText(switch (s.ordinal()) {
            case 0 -> "options.msf_friends.multiplayer_scope.off";
            case 1 -> "options.msf_friends.multiplayer_scope.lan";
            case 2 -> "options.msf_friends.multiplayer_scope.online";
            default -> "options.msf_friends.multiplayer_scope.off";
        });
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addScopeButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;
        this.msf$scope = MinecraftBridge.MultiplayerScope.LAN;

        // Port field: (width/2-75, 100, 150, 20). Button goes right of it.
        int x = this.width / 2 - 75 + 150 + 4;
        this.addButton(new ButtonWidget(x, 100, 60, 20, msf$label(this.msf$scope), btn -> {
            this.msf$scope = switch (this.msf$scope.ordinal()) {
                case 0 -> MinecraftBridge.MultiplayerScope.LAN;
                case 1 -> MinecraftBridge.MultiplayerScope.ONLINE;
                case 2 -> MinecraftBridge.MultiplayerScope.OFF;
                default -> MinecraftBridge.MultiplayerScope.LAN;
            };
            btn.setMessage(msf$label(this.msf$scope));
        }));
    }

    /**
     * Redirect the IntegratedServer.openToLan() call inside the "Start" button callback.
     * When scope is Off we skip publishing entirely and return false.
     * When LAN or Online we let it through.
     */
    @Redirect(method = "method_19851", remap = false, require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/server/integrated/IntegratedServer;openToLan(Lnet/minecraft/world/GameMode;ZI)Z"))
    private boolean msf$redirectPublish(IntegratedServer server, GameMode gameMode, boolean commands, int port) {
        if (this.msf$scope == MinecraftBridge.MultiplayerScope.OFF) {
            return false;
        }

        // Normal LAN publish
        boolean ok = server.openToLan(gameMode, commands, port);

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
            // LAN only - make sure scope is set to LAN (not ONLINE)
            var client = MsfFriendsBoot.get();
            if (client != null && client.bridge() != null) {
                client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.LAN);
            }
        }

        return ok;
    }
}
