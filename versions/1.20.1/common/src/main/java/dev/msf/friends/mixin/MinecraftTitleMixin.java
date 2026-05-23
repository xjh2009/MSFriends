package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Patches the window title to show "在线" instead of "局域网" when
 * the multiplayer scope is ONLINE (host) or the local client is
 * connected as a P2P guest.
 *
 * 1.20.1 Yarn: Minecraft → MinecraftClient.
 * In 1.20.1, createTitle is getWindowTitle or similar Yarn name.
 * We target the method using intermediary.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftTitleMixin {

    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true, require = 0)
    private void msf$patchTitle(CallbackInfoReturnable<String> cir) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;

        boolean online = client.bridge().multiplayerScope() == MinecraftBridge.MultiplayerScope.ONLINE
                || client.bridge().isConnectedViaP2P();
        if (!online) return;

        String title = cir.getReturnValue();
        String lanLabel = I18n.translate("title.multiplayer.lan");
        if (title.contains(lanLabel)) {
            String onlineLabel = I18n.translate("title.multiplayer.online");
            cir.setReturnValue(title.replace(lanLabel, onlineLabel));
        }
    }
}
