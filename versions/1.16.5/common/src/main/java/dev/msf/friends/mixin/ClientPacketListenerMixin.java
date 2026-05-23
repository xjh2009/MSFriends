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
 * On P2P connections, DTLS already secures transport, but we must still
 * prepare the profile key pair after login to ensure chat signing works.
 *
 * <p>Mirrors 26.1.2's ClientPacketListenerMixin which calls
 * {@code prepareKeyPair()} after handleLogin for RtcChannel connections.
 * In 1.16.5, there is no chat signing system, so this mixin serves as
 * a hook point for any future P2P-specific post-login logic.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPacketListenerMixin {

    @Shadow public abstract ClientConnection getConnection();

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void msf$onGameJoinForRtc(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (((ConnectionAccessor) this.getConnection()).msf$getChannel() instanceof RtcChannel) {
            // 1.16.5 has no chat signing / profile key pair system,
            // but this hook is preserved for P2P-specific post-login logic
            // that may be needed in the future (e.g., presence updates)
        }
    }
}