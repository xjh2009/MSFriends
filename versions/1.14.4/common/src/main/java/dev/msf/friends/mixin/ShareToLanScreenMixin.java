package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "局域网" wording with "在线" and
 * triggers a P2P host session when the user confirms opening to LAN.
 */
@Mixin(OpenToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {

    protected ShareToLanScreenMixin() {
        super(new LiteralText("dummy"));
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void msf$onInit(CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;
        client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.ONLINE);
    }

    @Inject(method = "method_19851", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void msf$onStart(CallbackInfo ci) {
        try {
            var client = MsfFriendsBoot.get();
            if (client == null || client.p2p() == null) return;
            var mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            var snap = dev.msf.friends.bridge.HeadlessMinecraftBridge.tryFetch();
            if (snap.isPresent()) {
                client.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.ONLINE);
                client.p2p().onHostScopeChanged(MinecraftBridge.MultiplayerScope.ONLINE);
            }
        } catch (Exception e) {
            // Log at debug to not break share-to-lan
        }
    }
}
