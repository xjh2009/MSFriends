package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On P2P connections DTLS already secures transport, so we must still prepare
 * the profile key pair after login to send chat session packets.
 *
 * 1.21.1: No prepareKeyPair/method_62151 on ClientPacketListener.
 * Instead, we trigger the key pair fetch via Minecraft.getProfileKeyPairManager()
 * and then call setKeyPair() on the packet listener.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow(remap = false) public abstract Connection method_48296();

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void msf$prepareKeyPairForRtc(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (((ConnectionAccessor) this.method_48296()).msf$getChannel() instanceof RtcChannel) {
            // In 1.21.1, manually request the profile key pair and apply it
            Minecraft mc = Minecraft.getInstance();
            mc.getProfileKeyPairManager().prepareKeyPair().thenAccept(keyPair -> {
                if (keyPair.isPresent()) {
                    mc.execute(() -> {
                        // Apply the key pair to the connection using setKeyPair
                        ClientPacketListener self = (ClientPacketListener) (Object) this;
                        self.setKeyPair(keyPair.get());
                    });
                }
            });
        }
    }
}