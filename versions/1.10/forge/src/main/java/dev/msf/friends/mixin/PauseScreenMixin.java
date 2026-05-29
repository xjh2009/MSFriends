package dev.msf.friends.mixin;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modifies the pause screen (in-game menu) to add Friends and multiplayer buttons.
 *
 * <p>On MC 1.10, the pause screen is {@link GuiIngameMenu}.
 */
@Mixin(GuiIngameMenu.class)
public abstract class PauseScreenMixin extends GuiScreen {

    private static final int MSF_FRIENDS_BUTTON_ID = 998;
    private static final int MSF_MULTIPLAYER_BUTTON_ID = 997;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void msf$addPauseButtons(CallbackInfo ci) {
        // Add "Friends" button below the existing LAN button
        int nextButtonY = this.height / 4 + 120 + 12;
        if (nextButtonY > this.height - 30) {
            nextButtonY = this.height - 30;
        }

        this.buttonList.add(new GuiButton(
                MSF_FRIENDS_BUTTON_ID,
                this.width / 2 - 100,
                nextButtonY,
                I18n.format("button.msf_friends.friends")
        ));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void msf$onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == MSF_FRIENDS_BUTTON_ID) {
            // TODO: mc.displayGuiScreen(new FriendsScreen(this));
            ci.cancel();
        }
    }
}
