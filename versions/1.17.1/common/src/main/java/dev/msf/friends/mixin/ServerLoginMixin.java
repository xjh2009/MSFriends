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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Server-side login Mixin for P2P connections on MC 1.17.1.
 * Same as 1.18.2 — authlib 3.x (fillProfileProperties, no fetchProfile).
 */
@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginMixin {
    private static final Logger LOGGER = Logging.get();

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private ClientConnection connection;
    @Shadow private GameProfile profile;

    @Shadow
    abstract void acceptPlayer();

    @Inject(method = "onHello", at = @At("HEAD"), cancellable = true, require = 0)
    private void msf$handlePreAuthenticated(LoginHelloC2SPacket packet, CallbackInfo ci) {
        Channel channel = ((ConnectionAccessor) this.connection).msf$getChannel();
        if (!(channel instanceof RtcChannel)) return;

        UUID intendedId = channel.attr(RtcChannel.INTENDED_PROFILE_ID).get();
        if (intendedId == null) return;

        String name = msf$getPacketName(packet);
        this.profile = this.msf$loadProfileProperties(intendedId, name);
        this.acceptPlayer();
        ci.cancel();
    }

    @Unique
    private static String msf$getPacketName(LoginHelloC2SPacket packet) {
        for (String methodName : new String[]{"name", "getProfileName", "getPlayerName"}) {
            try {
                java.lang.reflect.Method m = LoginHelloC2SPacket.class.getMethod(methodName);
                if (m.getReturnType() == String.class && m.getParameterCount() == 0) {
                    return (String) m.invoke(packet);
                }
            } catch (Exception ignored) {}
        }
        for (java.lang.reflect.Method m : LoginHelloC2SPacket.class.getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == String.class
                    && !m.getName().equals("toString") && !m.getName().equals("getClass")) {
                try {
                    return (String) m.invoke(packet);
                } catch (Exception ignored) {}
            }
        }
        return "Unknown";
    }

    private GameProfile msf$loadProfileProperties(UUID profileId, String fallbackName) {
        try {
            var sessionService = this.server.getSessionService();
            // authlib 4.x: fetchProfile(UUID, boolean) → ProfileResult → profile()
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
            } catch (NoSuchMethodException ignored) {}
            // Fallback: fillProfileProperties (authlib 3.x)
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
