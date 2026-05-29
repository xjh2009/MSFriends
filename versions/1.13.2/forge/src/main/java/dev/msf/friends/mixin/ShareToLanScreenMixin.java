package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "Open to LAN" screen behaviour for MC 1.13.2
 * with a three-state scope model (Off / LAN / Online).
 *
 * <p>1.13.2 MCP: GuiShareToLan, GuiScreen, GuiButtonExt,
 * initGui(), addButton(), IntegratedServer.shareToLAN().
 */
@Mixin(GuiShareToLan.class)
public abstract class ShareToLanScreenMixin extends GuiScreen {

    @Shadow @Final private GuiScreen lastScreen;

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

    @Inject(method = "initGui", at = @At("TAIL"))
    private void msf$addScopeButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;
        this.msf$scope = MinecraftBridge.MultiplayerScope.LAN;

        int x = this.width / 2 - 75 + 150 + 4;
        GuiButtonExt scopeBtn = new GuiButtonExt(9999, x, 160, 60, 20,
                msf$label(this.msf$scope)) {
            @Override
            public void onClick(double mouseX, double mouseY) {
                if (msf$scope == MinecraftBridge.MultiplayerScope.OFF) {
                    msf$scope = MinecraftBridge.MultiplayerScope.LAN;
                } else if (msf$scope == MinecraftBridge.MultiplayerScope.LAN) {
                    msf$scope = MinecraftBridge.MultiplayerScope.ONLINE;
                } else {
                    msf$scope = MinecraftBridge.MultiplayerScope.OFF;
                }
                this.displayString = msf$label(msf$scope);
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
