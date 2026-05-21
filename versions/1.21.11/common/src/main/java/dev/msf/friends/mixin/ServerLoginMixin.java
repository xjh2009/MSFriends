package dev.msf.friends.mixin;

import com.mojang.authlib.GameProfile;
import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;
import java.util.UUID;

/**
 * Server-side login Mixin for P2P connections:
 * 1. Skip AES encryption (DTLS already encrypts)
 * 2. Skip Mojang online authentication (identity pre-verified via signaling)
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginMixin {
    private static final Logger LOGGER = Logging.get();

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Connection connection;

    @Shadow private String requestedUsername;

    @Shadow
    abstract void startClientVerification(GameProfile profile);

    /**
     * Pre-authentication: if this connection has an intended profileId
     * (set during acceptGuest via RtcChannel attribute), skip the entire
     * online verification flow and go straight to startClientVerification
     * with the pre-verified identity.
     */
    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void msf$handlePreAuthenticated(ServerboundHelloPacket packet, CallbackInfo ci) {
        Channel channel = ((ConnectionAccessor) this.connection).msf$getChannel();
        if (!(channel instanceof RtcChannel)) return;

        // Check for pre-set intended profile ID
        UUID intendedId = channel.attr(RtcChannel.INTENDED_PROFILE_ID).get();
        if (intendedId == null) return;

        // Use the name from the hello packet + the pre-verified UUID
        String name = packet.name();
        this.requestedUsername = name;

        // Preserve the signed textures property so remote players do not fall back to Steve/Alex.
        GameProfile verifiedProfile = this.msf$loadProfileProperties(intendedId, name);
        this.startClientVerification(verifiedProfile);
        ci.cancel();
    }

    private GameProfile msf$loadProfileProperties(UUID profileId, String fallbackName) {
        try {
            var result = this.server.services().sessionService().fetchProfile(profileId, true);
            if (result != null && result.profile() != null) {
                GameProfile fetched = result.profile();
                if (fetched.name() == null || fetched.name().isBlank()) {
                    return new GameProfile(profileId, fallbackName, fetched.properties());
                }
                return fetched;
            }
        } catch (Throwable throwable) {
            LOGGER.debug("[p2p-login] failed to fetch profile properties for {}", profileId, throwable);
        }
        return new GameProfile(profileId, fallbackName);
    }

    /**
     * Skip AES encryption for RtcChannel — DTLS already provides transport encryption.
     */
    @Redirect(method = "handleKey",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/network/Connection;setEncryptionKey(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"))
    private void msf$skipServerEncryption(Connection connection, Cipher decrypt, Cipher encrypt) {
        if (((ConnectionAccessor) connection).msf$getChannel() instanceof RtcChannel) {
            return;
        }
        connection.setEncryptionKey(decrypt, encrypt);
    }
}
