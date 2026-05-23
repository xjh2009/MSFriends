package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.crypto.Cipher;

/**
 * Client-side: skip MC-layer AES encryption for P2P connections.
 *
 * <p>In MC 1.18.2, ClientHandshakePacketListenerImpl has a private helper
 * method_2893 (intermediary) that wraps the connection.setEncryptionKey call.
 * The encryption setup lambda is compiled into this method. Using
 * remap = false because intermediary names are stable across mapping changes.
 *
 * <p>Class 2535 = Connection, method 10746 = setEncryptionKey in intermediary.
 */
@Mixin(net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl.class)
public abstract class ClientLoginMixin {

    @Redirect(method = "method_2893", remap = false, require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/class_2535;method_10746(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"))
    private void msf$skipClientEncryption(Connection connection, Cipher decrypt, Cipher encrypt) {
        if (((ConnectionAccessor) connection).msf$getChannel() instanceof RtcChannel) {
            return;
        }
        connection.setEncryptionKey(decrypt, encrypt);
    }
}
