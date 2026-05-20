package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.crypto.Cipher;

/**
 * On the client side: skip MC-layer AES encryption for P2P connections.
 * The lambda inside setEncryption calls connection.setEncryptionKey — we redirect it.
 */
@Mixin(net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl.class)
public abstract class ClientLoginMixin {

    @Shadow @Final private Connection connection;

    /**
     * Redirect the setEncryptionKey call within the setEncryption lambda.
     * If channel is RtcChannel, skip — DTLS provides transport encryption.
     */
    @Redirect(method = "lambda$setEncryption$0",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/network/Connection;setEncryptionKey(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"))
    private void msf$skipClientEncryption(Connection connection, Cipher decrypt, Cipher encrypt) {
        if (((ConnectionAccessor) connection).msf$getChannel() instanceof RtcChannel) {
            return;
        }
        connection.setEncryptionKey(decrypt, encrypt);
    }
}
