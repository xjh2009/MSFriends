package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.AbstractButton;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ShareToLanScreen;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.util.text.StringTextComponent;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "Open to LAN" screen behaviour for MC 1.13.2
 * with a three-state scope model (Off / LAN / Online).
 *
 * <p>1.13.2 MCP: ShareToLanScreen, Screen, Button,
 * init(), addButton(), IntegratedServer.shareToLAN().
 */
@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {
    protected ShareToLanScreenMixin() { super(new net.minecraft.util.text.StringTextComponent("Share to LAN")); }

    @Shadow @Final private Screen lastScreen;

    @Unique private MinecraftBridge.MultiplayerScope msf$scope = MinecraftBridge.MultiplayerScope.LAN;

    @Unique
    private static String msf$label(MinecraftBridge.MultiplayerScope s) {
        if (s == MinecraftBridge.MultiplayerScope.OFF) {
            return net.minecraft.client.resources.I18n.format("options.msf_friends.multiplayer_scope.off");
        } else if (s == MinecraftBridge.MultiplayerScope.LAN) {
            return net.minecraft.client.resources.I18n.format("options.msf_friends.multiplayer_scope.lan");
        } else {
            return net.minecraft.client.resources.I18n.format("options.msf_friends.multiplayer_scope.online");
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addScopeButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;
        this.msf$scope = MinecraftBridge.MultiplayerScope.LAN;

        int x = this.width / 2 - 75 + 150 + 4;
        Button scopeBtn = new Button(x, 160, 60, 20,
                msf$label(this.msf$scope), b -> {}) {
            @Override
            public void onPress() {
                if (msf$scope == MinecraftBridge.MultiplayerScope.OFF) {
                    msf$scope = MinecraftBridge.MultiplayerScope.LAN;
                } else if (msf$scope == MinecraftBridge.MultiplayerScope.LAN) {
                    msf$scope = MinecraftBridge.MultiplayerScope.ONLINE;
                } else {
                    msf$scope = MinecraftBridge.MultiplayerScope.OFF;
                }
                this.setMessage(msf$label(msf$scope));
            }
        };
        this.addButton(scopeBtn);
    }

    /**
     * Inject into the publish button lambda to override scope behavior.
     * We intercept after the IntegratedServer.shareToLAN() call to set our scope.
     */
    @Inject(method = "lambda$initGui$0", at = @At("HEAD"), cancellable = true, require = 0)
    private void msf$redirectPublish(CallbackInfo ci) {
        MsfFriendsBoot client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;

        IntegratedServer server = net.minecraft.client.Minecraft.getInstance().getIntegratedServer();
        if (server == null) return;

        if (this.msf$scope == MinecraftBridge.MultiplayerScope.OFF) {
            ci.cancel();
            return;
        }

        client.bridge().setMultiplayerScope(this.msf$scope);
    }
}
