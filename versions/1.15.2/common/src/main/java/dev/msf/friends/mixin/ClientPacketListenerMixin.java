package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On P2P connections DTLS already secures transport.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    @Shadow @Final private ClientConnection connection;

    @Inject(method = "onGameJoin", at = @At("TAIL"), require = 0)
    private void msf$afterGameJoin(CallbackInfo ci) {
        if (((ConnectionAccessor) this.connection).msf$getChannel() instanceof RtcChannel) {
            // 1.14.4 does not require additional setup after login
        }
    }
}
