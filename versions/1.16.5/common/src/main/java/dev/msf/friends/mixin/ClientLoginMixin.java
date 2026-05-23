package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.network.ClientConnection;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.crypto.Cipher;

/**
 * On the client side: skip MC-layer AES encryption for P2P connections.
 *
 * <p>When the server sends a Key packet during login, the client normally
 * enables AES encryption on the connection. For P2P connections via RtcChannel,
 * DTLS already provides transport encryption, so we skip the MC-layer AES
 * to avoid double-encryption and cipher mismatches.
 *
 * <p>Mirrors 26.1.2's ClientLoginMixin which targets
 * {@code ClientHandshakePacketListenerImpl.lambda$setEncryption$0}.
 * In 1.16.5 Yarn, onHello submits a lambda to NetworkUtils.EXECUTOR;
 * the lambda body is method_2893 (private synthetic) which calls
 * {@code ClientConnection.setupEncryption(Cipher, Cipher)}.
 * We target method_2893 directly with remap=false to bypass refmap issues.
 */
@Mixin(ClientLoginNetworkHandler.class)
public abstract class ClientLoginMixin {

    @Shadow @Final private ClientConnection connection;

    /**
     * Redirect the setupEncryption call within method_2893 (the private
     * encryption-setup lambda body). If channel is RtcChannel, skip —
     * DTLS provides transport encryption.
     */
    @Redirect(method = "method_2893", remap = false, require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/network/ClientConnection;setupEncryption(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"))
    private void msf$skipClientEncryption(ClientConnection conn, Cipher decrypt, Cipher encrypt) {
        if (((ConnectionAccessor) conn).msf$getChannel() instanceof RtcChannel) {
            return;
        }
        conn.setupEncryption(decrypt, encrypt);
    }
}
