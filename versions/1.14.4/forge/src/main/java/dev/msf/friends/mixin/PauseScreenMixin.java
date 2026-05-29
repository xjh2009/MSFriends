package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.screen.SimpleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.util.text.StringTextComponent;
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
 *   <li>IngameMenuScreen (not GameMenuScreen)</li>
 *   <li>Button / Button (not ButtonWidget)</li>
 *   <li>init() (not initWidgets())</li>
 *   <li>addButton(T) inherited from Screen</li>
 *   <li>displayString field (not getMessage())</li>
 *   <li>IntegratedServer.getPublic() (not isRemote())</li>
 * </ul>
 */
@Mixin(IngameMenuScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin() { super(new net.minecraft.util.text.StringTextComponent("Pause")); }

    /**
     * Force showing the "Open to LAN" button even after publishing.
     */
    @Redirect(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/integrated/IntegratedServer;getPublic()Z"), require = 0)
    private boolean msf$forceShowLanButton(IntegratedServer server) {
        return false;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        int centerX = this.width / 2;
        Button optionsBtn = null;
        Button lanBtn = null;

        // Find existing buttons
        List<Button> foundButtons = new ArrayList<>();
        for (IGuiEventListener child : this.children) {
            if (child instanceof Button) {
                Button btn = (Button) child;
                foundButtons.add(btn);
                String msgStr = btn.getMessage();
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

            Button friendsBtn = new SimpleButton(centerX + 2, y, 100, 20,
                    net.minecraft.client.resources.I18n.format("button.msf_friends.friends"),
                    () -> msf$openFriends());
            this.addButton(friendsBtn);

            // Shift lower buttons
            for (Button btn : foundButtons) {
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
