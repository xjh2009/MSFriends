package dev.msf.friends.mixin;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Friends" button to the title screen.
 *
 * <p>On MC 1.10.2, the title screen is {@link GuiMainMenu}. We inject at the
 * end of {@code initGui} to append our button.
 */
@Mixin(GuiMainMenu.class)
public abstract class TitleScreenMixin extends GuiScreen {

    private static final int MSF_FRIENDS_BUTTON_ID = 999;

    @Inject(method = "addSingleplayerMultiplayerButtons", at = @At("TAIL"))
    private void msf$addFriendsButton(int y, int spacingY, CallbackInfo ci) {
        // Position: right of the existing buttons, bottom-right corner
        // MC 1.10 title screen buttons are laid out in a grid
        int buttonWidth = 98;
        int buttonHeight = 20;
        int x = this.width / 2 + 2;
        int y2 = this.height / 4 + 48 + 72 + 12; // below language/quit row

        // Add a compact friends button in the bottom-right
        this.buttonList.add(new GuiButton(
                MSF_FRIENDS_BUTTON_ID,
                this.width - buttonWidth - 4,
                this.height - buttonHeight - 4,
                buttonWidth,
                buttonHeight,
                net.minecraft.client.resources.I18n.format("button.msf_friends.friends")
        ));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void msf$onActionPerformed(GuiButton button, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (button.id == MSF_FRIENDS_BUTTON_ID) {
            // TODO: mc.displayGuiScreen(new FriendsScreen(this));
            ci.cancel();
        }
    }
}
