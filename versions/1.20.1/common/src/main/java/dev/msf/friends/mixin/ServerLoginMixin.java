package dev.msf.friends.mixin;

import com.mojang.authlib.GameProfile;
import dev.msf.friends.bridge.FabricReflect;
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
 * Server-side login Mixin for P2P connections:
 * 1. Skip AES encryption (DTLS already encrypts)
 * 2. Skip Mojang online authentication (identity pre-verified via signaling)
 *
 * 1.20.1 Yarn: ServerLoginPacketListenerImpl → ServerLoginNetworkHandler,
 * Connection → ClientConnection.
 *
 * All @Shadow and @Inject use Yarn names; refmap handles runtime remapping
 * to intermediary. Only unmapped methods (method_*) use remap=false.
 *
 * Uses ServerAccessor to access ApiServices (matching 26.1.2 pattern).
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

        // LoginHelloC2SPacket is a record in 1.20.1; the name accessor
        // may not be mapped as name() in Yarn (it could be comp_765).
        // Use FabricReflect to find the method on the actual class.
        String name;
        try {
            Class<?> helloClass = FabricReflect.mcClass(
                    "net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket");
            Method nameMethod = FabricReflect.mcMethod(helloClass, "name");
            name = (String) nameMethod.invoke(packet);
        } catch (Exception e) {
            LOGGER.debug("[p2p-login] could not get name from hello packet", e);
            name = "Player";
        }
        this.profile = this.msf$loadProfileProperties(intendedId, name);
        this.acceptPlayer();
        ci.cancel();
    }

    private GameProfile msf$loadProfileProperties(UUID profileId, String fallbackName) {
        try {
            GameProfile template = new GameProfile(profileId, fallbackName);
            // 1.20.1 authlib (4.0.43) uses fillProfileProperties(GameProfile, boolean)
            // instead of fetchProfile(UUID, boolean) which returns ProfileResult in 7.x.
            // Use ServerAccessor to reach ApiServices → sessionService, matching
            // 26.1.2's pattern of ((ServerAccessor) server).msf$getServices().
            var apiServices = ((ServerAccessor) this.server).msf$getApiServices();
            var sessionService = apiServices.comp_837();
            GameProfile filled = dev.msf.friends.util.ProfileHelper.fillProfile(sessionService, template);
            if (filled != null) {
                return filled;
            }
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
    private void msf$skipServerEncryption(ClientConnection conn, Cipher decrypt, Cipher encrypt) {
        if (((ConnectionAccessor) conn).msf$getChannel() instanceof RtcChannel) {
            return;
        }
        conn.setupEncryption(decrypt, encrypt);
    }
}
