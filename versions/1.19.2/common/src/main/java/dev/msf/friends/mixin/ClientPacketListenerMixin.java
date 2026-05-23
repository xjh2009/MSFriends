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
 * <p>In 26.1.2 (MC 26.x), this mixin calls {@code prepareKeyPair()} after login
 * to set up the chat session for sending signed chat messages on P2P connections
 * where MC-layer AES was skipped. MC 1.19.2 uses a different chat signing
 * mechanism that does not require a separate key-pair preparation step, so the
 * mixin exists as a structural match but performs no additional logic.
 *
 * <p>1.19.2 Yarn: ClientPacketListener → ClientPlayNetworkHandler,
 * ClientboundLoginPacket → GameJoinS2CPacket.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    public abstract ClientConnection getConnection();

    /**
     * After login on a P2P connection, no extra key-pair preparation is needed
     * in 1.19.2. The connection works without additional setup.
     */
    @Inject(method = "onGameJoin", at = @At("TAIL"), require = 0)
    private void msf$afterGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (((ConnectionAccessor) this.getConnection()).msf$getChannel() instanceof RtcChannel) {
            // 1.19.2 chat signing does not require prepareKeyPair();
            // the connection is functional as-is after the AES skip.
        }
    }
}
