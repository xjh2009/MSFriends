package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
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
 * Replaces the vanilla "Open to LAN" screen behaviour for MC 1.19.2
 * with a three-state scope model (Off / LAN / Online).
 *
 * <p>1.19.2 Yarn differences: OpenToLanScreen (not ShareToLanScreen),
 * GameMenuScreen (not PauseScreen), ButtonWidget constructor API,
 * method_19851 is the createButton callback.
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
        return Text.translatable(switch (s) {
            case OFF    -> "options.msf_friends.multiplayer_scope.off";
            case LAN    -> "options.msf_friends.multiplayer_scope.lan";
            case ONLINE -> "options.msf_friends.multiplayer_scope.online";
        });
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addScopeButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;
        this.msf$scope = MinecraftBridge.MultiplayerScope.LAN;

        // Port field is at (width/2 - 75, 160, 150, 20). Button goes right.
        int x = this.width / 2 - 75 + 150 + 4;
        this.addDrawableChild(
            new ButtonWidget(x, 160, 60, 20, msf$label(this.msf$scope), btn -> {
                this.msf$scope = switch (this.msf$scope) {
                    case OFF    -> MinecraftBridge.MultiplayerScope.LAN;
                    case LAN    -> MinecraftBridge.MultiplayerScope.ONLINE;
                    case ONLINE -> MinecraftBridge.MultiplayerScope.OFF;
                };
                btn.setMessage(msf$label(this.msf$scope));
            })
        );
    }

    /**
     * Redirect the {@code IntegratedServer.openToLan()} call.
     * In MC 1.19.2 Yarn the button callback uses method name method_19851.
     */
    @Redirect(method = "method_19851", remap = false, require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/class_1132;method_3763(Lnet/minecraft/class_1934;ZI)Z"))
    private boolean msf$redirectPublish(IntegratedServer server, GameMode gameMode, boolean commands, int port) {
        if (this.msf$scope == MinecraftBridge.MultiplayerScope.OFF) {
            return false;
        }

        // Mirror 26.1.2 ShareToLanScreenMixin: prepare multiplayer state.
        // 1.19.2 does not have prepareForMultiplayer() or prepareKeyPair()
        // (those are 1.21+ chat-signing features). The LAN openToLan call
        // below handles all the necessary setup for 1.19.2.

        // Normal LAN publish
        boolean ok = server.openToLan(gameMode, commands, port);

        if (ok && this.msf$scope == MinecraftBridge.MultiplayerScope.ONLINE) {
            var client = MsfFriendsBoot.get();
            if (client != null && client.bridge() != null && client.p2p() != null) {
                client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.ONLINE);
                client.p2p().onHostScopeChanged(MinecraftBridge.MultiplayerScope.ONLINE);
                if (client.social() != null) {
                    client.social().getPresenceHandler().tryUpdatePresence();
                }
            }
        } else if (ok) {
            var client = MsfFriendsBoot.get();
            if (client != null && client.bridge() != null) {
                client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.LAN);
            }
        }

        return ok;
    }
}
