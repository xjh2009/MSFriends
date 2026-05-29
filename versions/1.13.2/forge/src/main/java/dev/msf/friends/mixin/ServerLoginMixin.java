package dev.msf.friends.mixin;

import com.mojang.authlib.GameProfile;
import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.client.CPacketLoginStart;
import net.minecraft.network.NetHandlerLoginServer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Server-side login Mixin for P2P connections on MC 1.13.2:
 * 1. Skip AES encryption (DTLS already encrypts)
 * 2. Skip Mojang online authentication (identity pre-verified via signaling)
 *
 * 1.13.2 MCP: NetHandlerLoginServer,
 * NetworkManager, CPacketLoginStart.
 */
@Mixin(NetHandlerLoginServer.class)
public abstract class ServerLoginMixin {
    private static final Logger LOGGER = Logging.get();

    @Shadow @Final private MinecraftServer server;
    @Shadow public final NetworkManager networkManager = null;
    @Shadow private GameProfile loginGameProfile;

    @Shadow
    public abstract void tryAcceptPlayer();

    /**
     * Pre-authentication: if this connection has an intended profileId
     * (set during acceptGuest via RtcChannel attribute), skip the entire
     * online verification flow and go straight to acceptPlayer.
     */
    @Inject(method = "processLoginStart", at = @At("HEAD"), cancellable = true, require = 0)
    private void msf$handlePreAuthenticated(CPacketLoginStart packet, CallbackInfo ci) {
        Channel channel = ((ConnectionAccessor) this.networkManager).msf$getChannel();
        if (!(channel instanceof RtcChannel)) return;

        UUID intendedId = channel.attr(RtcChannel.INTENDED_PROFILE_ID).get();
        if (intendedId == null) return;

        String name = msf$getPacketName(packet);
        this.loginGameProfile = this.msf$loadProfileProperties(intendedId, name);
        this.tryAcceptPlayer();
        ci.cancel();
    }

    @Unique
    private static String msf$getPacketName(CPacketLoginStart packet) {
        // Try known MCP names for the player name field accessor
        for (String methodName : new String[]{"getName", "getProfileName", "getPlayerName"}) {
            try {
                Method m = CPacketLoginStart.class.getMethod(methodName);
                if (m.getReturnType() == String.class && m.getParameterCount() == 0) {
                    return (String) m.invoke(packet);
                }
            } catch (Exception ignored) {}
        }
        // Fallback: search all no-arg String-returning methods
        for (Method m : CPacketLoginStart.class.getMethods()) {
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
            com.mojang.authlib.minecraft.MinecraftSessionService sessionService = this.server.getMinecraftSessionService();
            // Try fillProfileProperties (authlib 2.x / 3.x pattern for 1.13.2)
            try {
                Method fillProfile = sessionService.getClass().getMethod(
                        "fillProfileProperties", GameProfile.class, boolean.class);
                GameProfile filled = (GameProfile) fillProfile.invoke(sessionService,
                        new GameProfile(profileId, fallbackName), true);
                if (filled != null) return filled;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable throwable) {
            LOGGER.debug("[p2p-login] failed to fetch profile properties for {}", profileId, throwable);
        }
        return new GameProfile(profileId, fallbackName);
    }
}
