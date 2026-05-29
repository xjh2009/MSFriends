package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On P2P connections DTLS already secures transport.
 *
 * <p>In 1.17.1, no additional key-pair preparation is needed after login
 * on a P2P connection.
 *
 * <p>1.17.1 Yarn: ClientPacketListener → ClientPlayNetworkHandler,
 * ClientboundLoginPacket → GameJoinS2CPacket.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    public abstract ClientConnection getConnection();

    @Inject(method = "onGameJoin", at = @At("TAIL"), require = 0)
    private void msf$afterGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (((ConnectionAccessor) this.getConnection()).msf$getChannel() instanceof RtcChannel) {
            // 1.17.1 does not require prepareKeyPair();
        }
    }
}
