package dev.msf.friends.mixin;

import com.mojang.authlib.GameProfile;
import dev.msf.friends.bridge.FabricReflect;
import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Server-side login mixin for MC 1.18.2.
 * Uses FabricReflect instead of @Accessor because the Mixin AP fails to
 * generate refmap entries for @Accessor when using official Mojang mappings.
 * 1.18.2 uses handleAcceptedLogin instead of verifyLoginAndFinishConnection/startClientVerification.
 * 1.18.2 uses server.getSessionService() instead of server.services().sessionService().
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginMixin {
    private static final Logger LOGGER = Logging.get();

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void msf$handlePreAuthenticated(ServerboundHelloPacket packet, CallbackInfo ci) {
        ServerLoginPacketListenerImpl self = (ServerLoginPacketListenerImpl) (Object) this;
        Channel channel;
        try {
            Field connField = FabricReflect.mcField(ServerLoginPacketListenerImpl.class, "connection");
            connField.setAccessible(true);
            Connection conn = (Connection) connField.get(self);
            channel = ((ConnectionAccessor) conn).msf$getChannel();
        } catch (Exception e) {
            LOGGER.error("[p2p-login] failed to get connection", e);
            return;
        }
        if (!(channel instanceof RtcChannel)) return;
        UUID intendedId = channel.attr(RtcChannel.INTENDED_PROFILE_ID).get();
        if (intendedId == null) return;

        try {
            Field gameProfileField = FabricReflect.mcField(ServerLoginPacketListenerImpl.class, "gameProfile");
            gameProfileField.setAccessible(true);
            GameProfile currentProfile = (GameProfile) gameProfileField.get(self);

            GameProfile verProf = new GameProfile(intendedId, currentProfile.name());
            try {
                Field serverField = FabricReflect.mcField(ServerLoginPacketListenerImpl.class, "server");
                serverField.setAccessible(true);
                MinecraftServer server = (MinecraftServer) serverField.get(self);
                var sess = server.getSessionService();
                var fetched = sess.fetchProfile(intendedId, true);
                if (fetched != null && fetched.profile() != null) {
                    GameProfile fp = fetched.profile();
                    if (fp.name() != null && !fp.name().isBlank())
                        verProf = new GameProfile(intendedId, fp.name(), fp.properties());
                }
            } catch (Throwable t) { LOGGER.debug("[p2p-login] skin fetch failed", t); }

            gameProfileField.set(self, verProf);

            Method handleAcceptedLogin = FabricReflect.mcDeclaredMethod(
                    ServerLoginPacketListenerImpl.class, "handleAcceptedLogin");
            handleAcceptedLogin.invoke(self);
        } catch (Exception e) {
            LOGGER.error("[p2p-login] failed to handle pre-authenticated login", e);
            return;
        }
        ci.cancel();
    }

    /**
     * Redirect setEncryptionKey in handleKey for server-side.
     * 1.18.2: handleKey is inherited from ServerLoginPacketListener interface.
     * Using Mojang mappings for both method name and @At target since the
     * Mixin AP will remap them via the refmap at runtime.
     */
    @Redirect(method = "handleKey", require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/network/Connection;setEncryptionKey(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"))
    private void msf$skipServerEncryption(Connection conn, Cipher d, Cipher e) {
        if (!(((ConnectionAccessor) conn).msf$getChannel() instanceof RtcChannel))
            conn.setEncryptionKey(d, e);
    }
}
