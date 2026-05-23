package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "Open to LAN" screen behaviour with a three-state
 * scope model (Off / LAN / Online), adapted for MC 1.18.2.
 *
 * <p>1.18.2 API: new Button(x, y, w, h, text, pressAction), TranslatableComponent, etc.
 * ShareToLanScreen has no port field in 1.18.2 — the port is passed through
 * to publishServer internally.
 *
 * <p>The button callback that calls publishServer is method_19851 (intermediary).
 * We use remap = false because intermediary → intermediary needs no remapping.
 */
@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {

    @Unique private MinecraftBridge.MultiplayerScope msf$scope = MinecraftBridge.MultiplayerScope.LAN;

    protected ShareToLanScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private static Component msf$label(MinecraftBridge.MultiplayerScope s) {
        return new TranslatableComponent(switch (s) {
            case OFF    -> "options.msf_friends.multiplayer_scope.off";
            case LAN    -> "options.msf_friends.multiplayer_scope.lan";
            case ONLINE -> "options.msf_friends.multiplayer_scope.online";
        });
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addScopeButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;
        this.msf$scope = MinecraftBridge.MultiplayerScope.LAN;

        // Port field: roughly (width/2 - 75, 160, 150, 20). Button goes right of it.
        int x = this.width / 2 - 75 + 150 + 4;
        this.addRenderableWidget(new Button(x, 160, 60, 20, msf$label(this.msf$scope), btn -> {
            this.msf$scope = switch (this.msf$scope) {
                case OFF    -> MinecraftBridge.MultiplayerScope.LAN;
                case LAN    -> MinecraftBridge.MultiplayerScope.ONLINE;
                case ONLINE -> MinecraftBridge.MultiplayerScope.OFF;
            };
            btn.setMessage(msf$label(this.msf$scope));
        }));
    }

    /**
     * Redirect the IntegratedServer.publishServer() call inside the "Start LAN" button callback.
     * In MC 1.18.2 the button callback that calls publishServer is compiled as
     * method_19851 (intermediary, no Mojang mapping). Using remap = false for stability.
     * Class 1132 = IntegratedServer, method 3763 = publishServer in intermediary.
     */
    @Redirect(method = "method_19851", remap = false, require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/class_1132;method_3763(Lnet/minecraft/class_1934;ZI)Z"))
    private boolean msf$redirectPublish(IntegratedServer server, GameType gameMode, boolean commands, int port) {
        if (this.msf$scope == MinecraftBridge.MultiplayerScope.OFF) {
            return false;
        }

        if (this.minecraft != null) {
            this.minecraft.prepareForMultiplayer();
        }

        boolean ok = server.publishServer(gameMode, commands, port);

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
