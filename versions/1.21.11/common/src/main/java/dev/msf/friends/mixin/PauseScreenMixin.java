package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
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

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    @Unique private GridLayout.RowHelper msf$rowHelper;
    @Unique private int msf$addChildCount;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Redirect(method = "createPauseMenu",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/server/IntegratedServer;isPublished()Z"))
    private boolean msf$forceShowLanButton(IntegratedServer server) {
        return false;
    }

    @Redirect(method = "createPauseMenu",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/layouts/GridLayout;createRowHelper(I)Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;"))
    private GridLayout.RowHelper msf$captureRowHelper(GridLayout grid, int columns) {
        this.msf$rowHelper = grid.createRowHelper(columns);
        this.msf$addChildCount = 0;
        return this.msf$rowHelper;
    }

    @Redirect(method = "createPauseMenu",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement msf$interceptAddChild(GridLayout.RowHelper rowHelper, LayoutElement element) {
        msf$addChildCount++;

        if (msf$addChildCount == 3 && element instanceof Button btn) {
            btn.setWidth(98);
            LayoutElement result = rowHelper.addChild(btn);
            if (MsfFriendsBoot.get() != null) {
                Button friendsBtn = Button.builder(
                        Component.translatable("button.msf_friends.friends"),
                        b -> {/* Friends screen not yet ported to 1.21.11 */}
                ).width(98).build();
                rowHelper.addChild(friendsBtn);
            }
            return result;
        }
        
        if (msf$addChildCount == 4 && element instanceof Button btn) {
            btn.setMessage(Component.translatable("button.msf_friends.multiplayer"));
            LayoutElement result = rowHelper.addChild(element);
            
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

        return rowHelper.addChild(element);
    }

    @Unique
    private void msf$openFriends() {
        // Friends screen not yet ported to 1.21.11
    }
}
