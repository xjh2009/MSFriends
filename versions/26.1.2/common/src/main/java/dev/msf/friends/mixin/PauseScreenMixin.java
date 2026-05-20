package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Pause screen changes (mirroring 26.2):
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
 *   (feedback if applicable)
 *   [ 选项 ] [ 好友 ]
 *   [多人游戏][举报玩家]
 *   [  保存并返回    ]
 * </pre>
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    @Unique private GridLayout.RowHelper msf$rowHelper;
    @Unique private int msf$addChildCount;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    /**
     * Force showing the "Open to LAN" button even after publishing.
     */
    @Redirect(method = "createPauseMenu",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/server/IntegratedServer;isPublished()Z"))
    private boolean msf$forceShowLanButton(IntegratedServer server) {
        return false;
    }

    /**
     * Capture the RowHelper when it's created.
     */
    @Redirect(method = "createPauseMenu",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/layouts/GridLayout;createRowHelper(I)Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;"))
    private GridLayout.RowHelper msf$captureRowHelper(GridLayout grid, int columns) {
        this.msf$rowHelper = grid.createRowHelper(columns);
        this.msf$addChildCount = 0;
        return this.msf$rowHelper;
    }

    /**
     * Intercept addChild calls to:
     * - Make OPTIONS half-width and add "好友" after it
     * - Add "举报玩家" after SHARE_TO_LAN
     */
    @Redirect(method = "createPauseMenu",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement msf$interceptAddChild(GridLayout.RowHelper rowHelper, LayoutElement element) {
        msf$addChildCount++;

        // Count: 1=ADVANCEMENTS, 2=STATS, 3=OPTIONS, 4=SHARE_TO_LAN, (5=PLAYER_REPORTING vanilla only if published)
        
        if (msf$addChildCount == 3 && element instanceof Button btn) {
            // OPTIONS button - make it half-width by changing its width directly
            btn.setWidth(98);
            LayoutElement result = rowHelper.addChild(btn);
            
            // Add "好友" button next to it
            if (MsfFriendsBoot.get() != null) {
                Button friendsBtn = Button.builder(
                        Component.translatable("button.msf_friends.friends"),
                        b -> msf$openFriends()
                ).width(98).build();
                rowHelper.addChild(friendsBtn);
            }
            return result;
        }
        
        if (msf$addChildCount == 4 && element instanceof Button btn) {
            // SHARE_TO_LAN button - rename and add to row
            btn.setMessage(Component.translatable("button.msf_friends.multiplayer"));
            LayoutElement result = rowHelper.addChild(element);
            
            // Add "举报玩家" button next to it
            Minecraft mc = Minecraft.getInstance();
            boolean published = mc.hasSingleplayerServer()
                    && mc.getSingleplayerServer() != null
                    && mc.getSingleplayerServer().isPublished();
            
            Button reportBtn = Button.builder(
                    Component.translatable("menu.playerReporting"),
                    b -> mc.setScreen(new SocialInteractionsScreen(this))
            ).width(98).build();
            reportBtn.active = published;
            rowHelper.addChild(reportBtn);
            
            return result;
        }

        // Default: pass through
        return rowHelper.addChild(element);
    }

    @Unique
    private void msf$openFriends() {
        Minecraft.getInstance().setScreen(new FriendsScreen(this));
    }
}
