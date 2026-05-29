package dev.msf.friends.mixin;

import com.mojang.authlib.GameProfile;
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

    /**
     * In 1.14.4, onHello handles the login start packet.
     * We capture the profile after the vanilla method completes.
     */
    @Inject(method = "onHello", at = @At("TAIL"), require = 0, remap = false)
    private void afterHelloCapture(CallbackInfo ci) {
        try {
            if (profile != null) {
                // authlib: GameProfile.id() returns UUID
                java.util.UUID id = profile.id();
                if (!msf$DEFAULT_SHADOW_HOST.equals(id)) {
                    dev.msf.friends.bridge.AcceptGuestBridge.pendingGuestProfile = profile;
                }
            }
        } catch (Exception ignored) {}
    }
}
