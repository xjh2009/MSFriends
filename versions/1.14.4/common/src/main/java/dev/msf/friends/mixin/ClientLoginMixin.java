package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.crypto.SecretKey;

/**
 * On the client side: skip MC-layer AES encryption for P2P connections.
 *
 * In 1.14.4, setupEncryption takes SecretKey.
 */
@Mixin(net.minecraft.client.network.ClientLoginNetworkHandler.class)
public abstract class ClientLoginMixin {

    @Shadow @Final private ClientConnection connection;

    @Redirect(method = "*", remap = false, require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/network/ClientConnection;setupEncryption(Ljavax/crypto/SecretKey;)V"))
    private void msf$skipClientEncryption(ClientConnection conn, SecretKey key) {
        if (((ConnectionAccessor) conn).msf$getChannel() instanceof RtcChannel) {
            return;
        }
        conn.setupEncryption(key);
    }
}
