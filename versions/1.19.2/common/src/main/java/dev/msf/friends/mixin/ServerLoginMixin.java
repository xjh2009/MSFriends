package dev.msf.friends.mixin;

import com.mojang.authlib.GameProfile;
import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Server-side login Mixin for P2P connections on MC 1.19.2:
 * 1. Skip AES encryption (DTLS already encrypts)
 * 2. Skip Mojang online authentication (identity pre-verified via signaling)
 *
 * <p>1.19.2 Yarn: ServerLoginPacketListenerImpl → ServerLoginNetworkHandler,
 * Connection → ClientConnection, ServerboundHelloPacket → LoginHelloC2SPacket.
 *
 * <p>Preserve the signed textures property so remote players do not fall back
 * to Steve/Alex. On 1.19.2's authlib 3.x, we use
 * {@code fillProfileProperties(GameProfile, boolean)} directly (no ProfileHelper).
 */
@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginMixin {
    private static final Logger LOGGER = Logging.get();

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private ClientConnection connection;
    @Shadow private GameProfile profile;

    @Shadow
    abstract void acceptPlayer();

    /**
     * Pre-authentication: if this connection has an intended profileId
     * (set during acceptGuest via RtcChannel attribute), skip the entire
     * online verification flow and go straight to acceptPlayer
     * with the pre-verified identity.
     */
    @Inject(method = "onHello", at = @At("HEAD"), cancellable = true, require = 0)
    private void msf$handlePreAuthenticated(LoginHelloC2SPacket packet, CallbackInfo ci) {
        Channel channel = ((ConnectionAccessor) this.connection).msf$getChannel();
        if (!(channel instanceof RtcChannel)) return;

        UUID intendedId = channel.attr(RtcChannel.INTENDED_PROFILE_ID).get();
        if (intendedId == null) return;

        String name = packet.comp_765();
        this.profile = this.msf$loadProfileProperties(intendedId, name);
        this.acceptPlayer();
        ci.cancel();
    }

    private GameProfile msf$loadProfileProperties(UUID profileId, String fallbackName) {
        try {
            var sessionService = this.server.getSessionService();
            // authlib 3.x: fillProfileProperties(GameProfile, boolean)
            // authlib 4.x+: fetchProfile(UUID, boolean) → ProfileResult → profile()
            // Try fetchProfile first (newer authlib), then fall back to fillProfileProperties.
            try {
                Method fetchProfile = sessionService.getClass().getMethod("fetchProfile", UUID.class, boolean.class);
                Object result = fetchProfile.invoke(sessionService, profileId, true);
                if (result != null) {
                    Method profileMethod = result.getClass().getMethod("profile");
                    GameProfile fetched = (GameProfile) profileMethod.invoke(result);
                    if (fetched != null) {
                        if (fetched.name() == null || fetched.name().isBlank()) {
                            return new GameProfile(profileId, fallbackName, fetched.properties());
                        }
                        return fetched;
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // authlib 3.x — fall through to fillProfileProperties
            }
            try {
                Method fillProfile = sessionService.getClass().getMethod("fillProfileProperties", GameProfile.class, boolean.class);
                GameProfile filled = (GameProfile) fillProfile.invoke(sessionService, new GameProfile(profileId, fallbackName), true);
                if (filled != null) return filled;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable throwable) {
            LOGGER.debug("[p2p-login] failed to fetch profile properties for {}", profileId, throwable);
        }
        return new GameProfile(profileId, fallbackName);
    }

    /**
     * Skip AES encryption for RtcChannel — DTLS already provides transport encryption.
     */
    @Redirect(method = "onKey", require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/network/ClientConnection;setupEncryption(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"))
    private void msf$skipServerEncryption(ClientConnection connection, Cipher decrypt, Cipher encrypt) {
        if (((ConnectionAccessor) connection).msf$getChannel() instanceof RtcChannel) {
            return;
        }
        connection.setupEncryption(decrypt, encrypt);
    }
}
