package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SJoinGamePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On P2P connections DTLS already secures transport.
 *
 * <p>1.13.2 MCP: ClientPlayNetHandler (not ClientPlayNetworkHandler),
 * NetworkManager (not ClientConnection), SJoinGamePacket.
 */
@Mixin(ClientPlayNetHandler.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    public abstract NetworkManager getNetworkManager();

    @Inject(method = "handleJoinGame", at = @At("TAIL"), require = 0)
    private void msf$afterGameJoin(SJoinGamePacket packet, CallbackInfo ci) {
        if (((ConnectionAccessor) this.getNetworkManager()).msf$getChannel() instanceof RtcChannel) {
            // 1.13.2 does not require prepareKeyPair();
            // the connection is functional as-is after the AES skip.
        }
    }
}
