package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.crypto.Cipher;

/**
 * On the client side: skip MC-layer AES encryption for P2P connections.
 *
 * <p>1.17.1 Yarn: ClientHandshakePacketListenerImpl → ClientLoginNetworkHandler,
 * Connection → ClientConnection.
 *
 * method_2893 is unmapped (private encryption setup lambda) — uses remap=false.
 */
@Mixin(net.minecraft.client.network.ClientLoginNetworkHandler.class)
public abstract class ClientLoginMixin {

    @Shadow @Final private ClientConnection connection;

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
