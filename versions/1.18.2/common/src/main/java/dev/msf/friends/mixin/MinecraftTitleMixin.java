package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void msf$patchTitle(CallbackInfoReturnable<String> cir) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;
        boolean online = client.bridge().multiplayerScope() == MinecraftBridge.MultiplayerScope.ONLINE
                || client.bridge().isConnectedViaP2P();
        if (!online) return;
        String title = cir.getReturnValue();
        String lanLabel = I18n.get("title.multiplayer.lan");
        if (title.contains(lanLabel)) {
            String onlineLabel = I18n.get("title.multiplayer.online");
            cir.setReturnValue(title.replace(lanLabel, onlineLabel));
        }
    }
}
