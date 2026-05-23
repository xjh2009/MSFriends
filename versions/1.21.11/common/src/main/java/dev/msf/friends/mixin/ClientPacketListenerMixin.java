package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 no longer ties secure-chat bootstrap to MC-layer AES encryption.
 * On P2P connections DTLS already secures transport, so we must still prepare
 * the profile key pair after login to send ServerboundChatSessionUpdatePacket.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow(remap = false) public abstract Connection method_48296();

    /**
     * In intermediary mappings, prepareKeyPair is method_62151.
     * Using remap = false because the refmap doesn't include Shadow methods.
     */
    @Shadow(remap = false) public abstract void method_62151();

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void msf$prepareKeyPairForRtc(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (((ConnectionAccessor) this.method_48296()).msf$getChannel() instanceof RtcChannel) {
            this.method_62151();
        }
    }
}