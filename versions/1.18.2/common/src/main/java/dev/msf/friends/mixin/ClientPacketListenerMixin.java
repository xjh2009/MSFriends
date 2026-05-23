package dev.msf.friends.mixin;

import dev.msf.friends.bridge.FabricReflect;
import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * On P2P connections DTLS already secures transport.
 * 1.18.2 has no separate key-pair preparation (no secure chat).
 *
 * Uses FabricReflect instead of @Accessor because the Mixin AP fails to
 * generate refmap entries for @Accessor when using official Mojang mappings.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void msf$prepareKeyPairForRtc(ClientboundLoginPacket packet, CallbackInfo ci) {
        try {
            Field connField = FabricReflect.mcField(ClientPacketListener.class, "connection");
            connField.setAccessible(true);
            Connection conn = (Connection) connField.get(this);
            if (((ConnectionAccessor) conn).msf$getChannel() instanceof RtcChannel) {
                // 1.18.2: no separate key-pair preparation needed; DTLS already secures transport
            }
        } catch (Exception e) {
            // Non-fatal
        }
    }
}
