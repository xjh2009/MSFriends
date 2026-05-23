package dev.msf.friends.mixin;

import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.encryption.PlayerKeyPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On P2P connections DTLS already secures transport, so we must still prepare
 * the profile key pair after login to send chat session packets.
 *
 * 1.20.1 Yarn: ClientPacketListener → ClientPlayNetworkHandler,
 * Connection → ClientConnection, ClientboundLoginPacket → LoginS2CPacket.
 *
 * Yarn names used; refmap handles intermediary remapping at runtime.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    @Shadow public abstract ClientConnection getConnection();

    @Shadow public abstract void updateKeyPair(PlayerKeyPair keyPair);

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void msf$prepareKeyPairForRtc(CallbackInfo ci) {
        if (((ConnectionAccessor) this.getConnection()).msf$getChannel() instanceof RtcChannel) {
            // In 1.20.1, the profile key pair is loaded in Minecraft instance
            // We trigger a key pair refresh via the Minecraft client
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            var profileKeys = mc.getProfileKeys();
            if (profileKeys != null) {
                profileKeys.fetchKeyPair().thenAccept(keyPair -> {
                    if (keyPair.isPresent()) {
                        this.updateKeyPair(keyPair.get());
                    }
                });
            }
        }
    }
}
