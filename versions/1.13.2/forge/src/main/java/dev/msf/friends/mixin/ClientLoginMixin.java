package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.server.SPacketEncryptionRequest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.SecretKey;

/**
 * On the client side: skip MC-layer AES encryption for P2P connections.
 * 1.13.2 MCP: NetHandlerLoginClient, NetworkManager.enableEncryption(SecretKey).
 */
@Mixin(NetHandlerLoginClient.class)
public abstract class ClientLoginMixin {

    @Shadow @Final private NetworkManager networkManager;

    @Inject(method = "handleEncryptionRequest", at = @At("HEAD"), cancellable = true, require = 0)
    private void msf$skipClientEncryption(SPacketEncryptionRequest packet, CallbackInfo ci) {
        if (((ConnectionAccessor) this.networkManager).msf$getChannel() instanceof RtcChannel) {
            ci.cancel();
        }
    }
}
