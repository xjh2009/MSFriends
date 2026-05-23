package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "Open to LAN" screen behaviour with a three-state
 * scope model (Off / LAN / Online).
 *
 * <p>1.20.1 Yarn: ShareToLanScreen → OpenToLanScreen, GameType → GameMode,
 * Component → Text, Button → ButtonWidget, IntegratedServer same name.
 */
@Mixin(OpenToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {

    @Shadow @Final private Screen parent;
    @Shadow private int port;

    @Unique private MinecraftBridge.MultiplayerScope msf$scope = MinecraftBridge.MultiplayerScope.LAN;

    protected ShareToLanScreenMixin(Text title) {
        super(title);
    }

    @Unique
    private static Text msf$label(MinecraftBridge.MultiplayerScope s) {
        return Text.translatable(switch (s) {
            case OFF    -> "options.msf_friends.multiplayer_scope.off";
            case LAN    -> "options.msf_friends.multiplayer_scope.lan";
            case ONLINE -> "options.msf_friends.multiplayer_scope.online";
        });
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addScopeButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;
        this.msf$scope = MinecraftBridge.MultiplayerScope.LAN;

        int x = this.width / 2 - 75 + 150 + 4;
        this.addDrawableChild(
            ButtonWidget.builder(msf$label(this.msf$scope), btn -> {
                this.msf$scope = switch (this.msf$scope) {
                    case OFF    -> MinecraftBridge.MultiplayerScope.LAN;
                    case LAN    -> MinecraftBridge.MultiplayerScope.ONLINE;
                    case ONLINE -> MinecraftBridge.MultiplayerScope.OFF;
                };
                btn.setMessage(msf$label(this.msf$scope));
            }).dimensions(x, 160, 60, 20).build()
        );
    }

    /**
     * Redirect the IntegratedServer.openToLan() call.
     * In MC 1.20.1 Yarn, the button callback that calls openToLan is
     * compiled as method_19851. Use remap=false for intermediary stability.
     *
     * Matching 26.1.2: call prepareForMultiplayer() + connection key pair
     * preparation before publishing. In 1.20.1 Yarn the method may not
     * have a mapped name, so we call it via try/catch.
     */
    @Redirect(method = "method_19851", remap = false, require = 0,
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/class_1132;method_3763(Lnet/minecraft/class_1934;ZI)Z"))
    private boolean msf$redirectPublish(IntegratedServer server, GameMode gameMode, boolean commands, int port) {
        if (this.msf$scope == MinecraftBridge.MultiplayerScope.OFF) {
            return false;
        }

        // Mirror 26.1.2: prepare multiplayer state and force the host's
        // local connection to publish its chat session.
        if (this.client != null) {
            // prepareForMultiplayer() may not exist in 1.20.1 Yarn.
            // Call via FabricReflect (Yarn name → intermediary).
            try {
                ClassLoader cl = this.client.getClass().getClassLoader();
                Class.forName("net.minecraft.client.MinecraftClient", false, cl);
                var mcClass = this.client.getClass();
                try {
                    var m = dev.msf.friends.bridge.FabricReflect.mcMethod(mcClass, "prepareForMultiplayer");
                    try {
                        m.invoke(this.client);
                    } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException ignore) {
                        // Not accessible — not critical for P2P
                    }
                } catch (NoSuchMethodException ignore) {
                    // Method not available in this MC version — not critical
                }
            } catch (ClassNotFoundException ignore) {}
            var connection = this.client.getNetworkHandler();
            if (connection != null) {
                // 1.20.1 Yarn: profileKeys → fetch key pair for chat signing
                var profileKeys = this.client.getProfileKeys();
                if (profileKeys != null) {
                    profileKeys.fetchKeyPair().thenAccept(keyPair -> {
                        if (keyPair.isPresent()) {
                            connection.updateKeyPair(keyPair.get());
                        }
                    });
                }
            }
        }

        boolean ok = server.openToLan(gameMode, commands, port);

        if (ok && this.msf$scope == MinecraftBridge.MultiplayerScope.ONLINE) {
            var msfClient = MsfFriendsBoot.get();
            if (msfClient != null && msfClient.bridge() != null && msfClient.p2p() != null) {
                msfClient.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.ONLINE);
                msfClient.p2p().onHostScopeChanged(MinecraftBridge.MultiplayerScope.ONLINE);
                if (msfClient.social() != null) {
                    msfClient.social().getPresenceHandler().tryUpdatePresence();
                }
            }
        } else if (ok) {
            var msfClient = MsfFriendsBoot.get();
            if (msfClient != null && msfClient.bridge() != null) {
                msfClient.bridge().setMultiplayerScope(MinecraftBridge.MultiplayerScope.LAN);
            }
        }

        return ok;
    }
}
