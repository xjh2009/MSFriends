package dev.msf.friends.mixin;

import com.mojang.authlib.GameProfile;
import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
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
import java.util.UUID;

/**
 * Server-side login Mixin for P2P connections on MC 1.16.5:
 * 1. Skip AES encryption (DTLS already encrypts) — via onKey redirect
 * 2. Skip Mojang online auth for pre-authenticated P2P guests — via onHello
 *
 * Yarn target: net.minecraft.server.network.ServerLoginNetworkHandler
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
     * Pre-authentication: if this connection has an intended profileId,
     * accept the player directly without Mojang online verification.
     */
    @Inject(method = "onHello", at = @At("HEAD"), cancellable = true, require = 0)
    private void msf$handlePreAuthenticated(LoginHelloC2SPacket packet, CallbackInfo ci) {
        Channel channel = ((ConnectionAccessor) this.connection).msf$getChannel();
        if (!(channel instanceof RtcChannel)) return;

        UUID intendedId = channel.attr(RtcChannel.INTENDED_PROFILE_ID).get();
        if (intendedId == null) return;

        // Get player name from the packet via reflection (avoid compile-time GameProfile.getName() incompatibility)
        String name = intendedId.toString().substring(0, 8);
        try {
            java.lang.reflect.Method getProfile = packet.getClass().getMethod("getProfile");
            Object profile = getProfile.invoke(packet);
            if (profile != null) {
                try {
                    java.lang.reflect.Method gn = profile.getClass().getMethod("getName");
                    Object n = gn.invoke(profile);
                    if (n != null && !n.toString().isBlank()) name = n.toString();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            try {
                java.lang.reflect.Method getGameProfile = packet.getClass().getMethod("getGameProfile");
                Object profile = getGameProfile.invoke(packet);
                if (profile != null) {
                    try {
                        java.lang.reflect.Method gn = profile.getClass().getMethod("getName");
                        Object n = gn.invoke(profile);
                        if (n != null && !n.toString().isBlank()) name = n.toString();
                    } catch (Exception ignored2) {}
                }
            } catch (Exception ignored2) {}
        }

        this.profile = msf$loadProfileProperties(intendedId, name);
        this.acceptPlayer();
        ci.cancel();
    }

    private GameProfile msf$loadProfileProperties(UUID profileId, String fallbackName) {
        try {
            java.lang.reflect.Method getSessionService = this.server.getClass().getMethod("getSessionService");
            Object sessionService = getSessionService.invoke(this.server);
            java.lang.reflect.Method fetchProfile = sessionService.getClass().getMethod("fetchProfile", UUID.class, boolean.class);
            Object result = fetchProfile.invoke(sessionService, profileId, true);
            if (result != null) {
                java.lang.reflect.Method profileMethod = result.getClass().getMethod("profile");
                Object fetched = profileMethod.invoke(result);
                if (fetched instanceof GameProfile) {
                    return (GameProfile) fetched;
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[p2p-login] failed to fetch profile for {}", profileId, t);
        }
        return new GameProfile(profileId, fallbackName);
    }

    /**
     * Skip AES encryption for RtcChannel — DTLS already provides transport encryption.
     */
    @Redirect(method = "onKey",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/network/ClientConnection;setupEncryption(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"),
              require = 0)
    private void msf$skipServerEncryption(ClientConnection conn, Cipher decrypt, Cipher encrypt) {
        if (((ConnectionAccessor) conn).msf$getChannel() instanceof RtcChannel) {
            return;
        }
        conn.setupEncryption(decrypt, encrypt);
    }
}