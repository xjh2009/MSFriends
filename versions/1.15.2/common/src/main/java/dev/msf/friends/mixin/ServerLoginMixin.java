package dev.msf.friends.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Captures the connecting client's {@link GameProfile} as soon as
 * the vanilla login sequence finishes, so that the guest-acceptor
 * can access it without sending additional packets.
 */
@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginMixin {

    @Shadow
    private GameProfile profile;

    @Unique
    private static final UUID msf$DEFAULT_SHADOW_HOST = UUID.fromString("619b0b3f-9e37-49fc-a11e-fb07a1765c59");

    @Inject(method = "onHello", at = @At("HEAD"))
    private void captureProfileFromHello(LoginHelloC2SPacket packet, CallbackInfo ci) {
        // In 1.15.2, onHello handles the login start packet.
        // We just capture the profile from shadow after the vanilla method runs.
    }

    @Inject(method = "onHello", at = @At("TAIL"))
    private void afterHelloCapture(LoginHelloC2SPacket packet, CallbackInfo ci) {
        try {
            if (profile != null && !msf$DEFAULT_SHADOW_HOST.equals(profile.getId())) {
                dev.msf.friends.bridge.AcceptGuestBridge.pendingGuestProfile = profile;
            }
        } catch (Exception ignored) {}
    }
}
