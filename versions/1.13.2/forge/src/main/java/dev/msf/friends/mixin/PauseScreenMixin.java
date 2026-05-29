package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.screen.SimpleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Pause screen changes for MC 1.13.2.
 *
 * <p>1.13.2 MCP names:
 * <ul>
 *   <li>GuiIngameMenu (not GameMenuScreen)</li>
 *   <li>GuiButton / GuiButtonExt (not ButtonWidget)</li>
 *   <li>initGui() (not initWidgets())</li>
 *   <li>addButton(T) inherited from GuiScreen</li>
 *   <li>displayString field (not getMessage())</li>
 *   <li>IntegratedServer.getPublic() (not isRemote())</li>
 * </ul>
 */
@Mixin(GuiIngameMenu.class)
public abstract class PauseScreenMixin extends GuiScreen {

    /**
     * Force showing the "Open to LAN" button even after publishing.
     */
    @Redirect(method = "initGui", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/integrated/IntegratedServer;getPublic()Z"), require = 0)
    private boolean msf$forceShowLanButton(IntegratedServer server) {
        return false;
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        int centerX = this.width / 2;
        GuiButton optionsBtn = null;
        GuiButton lanBtn = null;

        // Find existing buttons
        List<GuiButton> foundButtons = new ArrayList<>();
        for (IGuiEventListener child : this.getChildren()) {
            if (child instanceof GuiButton) {
                GuiButton btn = (GuiButton) child;
                foundButtons.add(btn);
                String msgStr = btn.displayString;
                if (msgStr.contains("Option") || msgStr.contains("选项")) {
                    optionsBtn = btn;
                }
                if (msgStr.contains("LAN") || msgStr.contains("局域网")) {
                    lanBtn = btn;
                }
            }
        }

        // Resize Options button and add Friends button on the right
        if (optionsBtn != null) {
            int y = optionsBtn.y;
            optionsBtn.setWidth(100);
            optionsBtn.x = centerX - 102;

            GuiButton friendsBtn = new SimpleButton(centerX + 2, y, 100, 20,
                    net.minecraft.client.resources.I18n.format("button.msf_friends.friends"),
                    () -> msf$openFriends());
            this.addButton(friendsBtn);

            // Shift lower buttons
            for (GuiButton btn : foundButtons) {
                if (btn != optionsBtn && btn != lanBtn && btn.y > y) {
                    btn.y += 24;
                }
            }
        }
    }

    @Unique
    private void msf$openFriends() {
        Minecraft mc = Minecraft.getInstance();
        mc.displayGuiScreen(new FriendsScreen(this));
    }
}
