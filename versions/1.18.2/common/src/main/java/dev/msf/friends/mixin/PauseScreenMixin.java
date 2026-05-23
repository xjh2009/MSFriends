package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pause screen changes (mirroring 26.2 layout in 1.18.2 button API):
 * <ol>
 *   <li>OPTIONS row becomes half-width with "好友" button on the right</li>
 *   <li>"Open to LAN" renamed to "多人游戏", always shown</li>
 *   <li>"举报玩家" always shown (greyed when not published)</li>
 * </ol>
 *
 * Target layout:
 * <pre>
 *   [    返回游戏    ]
 *   [ 进度 ] [ 统计 ]
 *   [ 选项 ] [ 好友 ]
 *   [多人游戏][举报玩家]
 *   [  保存并返回    ]
 * </pre>
 *
 * 1.18.2 adaptation: No GridLayout/RowHelper available; uses manual Button
 * positioning with Button(x,y,w,h,text,pressAction) constructor.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) { super(title); }

    /**
     * Force showing the "Open to LAN" button even after publishing.
     */
    @Redirect(method = "createPauseMenu",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/server/IntegratedServer;isPublished()Z"))
    private boolean msf$forceShowLanButton(IntegratedServer server) { return false; }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;
        int centerX = this.width / 2;

        // --- Find and modify existing buttons ---
        // In 1.18.2 PauseScreen, buttons are added in createPauseMenu in this order:
        // 1. "Return to Game" (full width, y = height/4 + 72)
        // 2. "Advancements"  (half width left, y = height/4 + 96)
        // 3. "Stats"          (half width right, y = height/4 + 96)
        // 4. "Options"        (full width, y = height/4 + 120)  ← make half-width + add Friends
        // 5. "Share to LAN"   (half width left, y = height/4 + 144) ← rename + add Report
        // 6. "Save and Quit"  (full width or half width, y varies)

        // --- OPTIONS: shrink to half-width, add Friends next to it ---
        for (var w : this.children()) {
            if (w instanceof Button btn) {
                Component msg = btn.getMessage();
                if (msg != null && msg.equals(new TranslatableComponent("menu.options"))) {
                    // Shrink OPTIONS to half width
                    btn.setWidth(98);
                    btn.x = centerX - 99;
                    break;
                }
            }
        }

        // Add "好友" button next to OPTIONS
        int optionsY = this.height / 4 + 120;
        this.addRenderableWidget(new Button(centerX + 1, optionsY, 98, 20,
                new TranslatableComponent("button.msf_friends.friends"),
                b -> msf$openFriends()));

        // --- SHARE TO LAN: rename to "多人游戏", add "举报玩家" next to it ---
        for (var w : this.children()) {
            if (w instanceof Button btn) {
                Component msg = btn.getMessage();
                // "Share to LAN" button - vanilla key is "menu.shareToLan"
                if (msg != null && msg.equals(new TranslatableComponent("menu.shareToLan"))) {
                    btn.setWidth(98);
                    btn.x = centerX - 99;
                    btn.setMessage(new TranslatableComponent("button.msf_friends.multiplayer"));
                    break;
                }
            }
        }

        // Add "举报玩家" button next to MULTIPLAYER
        // 1.18.2: SocialInteractionsScreen may or may not exist.
        // Use reflection to safely instantiate it at runtime.
        int lanY = this.height / 4 + 144;
        Minecraft mc = Minecraft.getInstance();
        boolean published = mc.hasSingleplayerServer()
                && mc.getSingleplayerServer() != null
                && mc.getSingleplayerServer().isPublished();
        Button reportBtn = new Button(centerX + 1, lanY, 98, 20,
                new TranslatableComponent("menu.playerReporting"),
                b -> {
                    try {
                        Class<?> sic = Class.forName("net.minecraft.client.gui.screens.social.SocialInteractionsScreen");
                        mc.setScreen((Screen) sic.getDeclaredConstructor().newInstance());
                    } catch (Exception e) {
                        // SocialInteractionsScreen not available in this MC version
                    }
                });
        reportBtn.active = published;
        this.addRenderableWidget(reportBtn);
    }

    @Unique
    private void msf$openFriends() {
        Minecraft.getInstance().setScreen(new FriendsScreen(this));
    }
}
