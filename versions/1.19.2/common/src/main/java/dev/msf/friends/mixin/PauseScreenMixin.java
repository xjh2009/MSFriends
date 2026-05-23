package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pause screen changes for MC 1.19.2, mirroring 26.1.2's layout:
 * <ol>
 *   <li>"Open to LAN" button always shown (even after publishing)</li>
 *   <li>"Open to LAN" renamed to "多人游戏"</li>
 *   <li>"举报玩家" button always shown (greyed when not published)</li>
 *   <li>OPTIONS row becomes half-width with "好友" button on the right</li>
 * </ol>
 *
 * <p>1.19.2 Yarn: PauseScreen → GameMenuScreen, uses ButtonWidget constructor.
 * No GridLayout/RowHelper — we directly manipulate button positions.
 */
@Mixin(GameMenuScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Text title) {
        super(title);
    }

    /**
     * Force showing the "Open to LAN" button even after publishing.
     * 26.1.2 does the same by overriding the condition that hides it.
     */
    @Redirect(method = "initWidgets", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/integrated/IntegratedServer;isRemote()Z"), require = 0)
    private boolean msf$forceShowLanButton(IntegratedServer server) {
        return false;
    }

    @Inject(method = "initWidgets", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        // In 1.19.2 GameMenuScreen, buttons are centered at width/2.
        // We need to find the existing Options and LAN buttons by iterating children,
        // then resize them to half-width and add our buttons on the right.
        int centerX = this.width / 2;
        ButtonWidget optionsBtn = null;
        ButtonWidget lanBtn = null;

        for (var child : this.children()) {
            if (child instanceof ButtonWidget btn) {
                Text msg = btn.getMessage();
                String msgStr = msg.getString();
                // Options button: "Options" / "选项"
                if (msgStr.equals("Options") || msgStr.equals("选项")) {
                    optionsBtn = btn;
                }
                // LAN button: contains "LAN" / "局域网"
                if (msgStr.contains("LAN") || msgStr.contains("局域网")) {
                    lanBtn = btn;
                }
            }
        }

        // Resize Options button to half width and add "好友" button on the right
        if (optionsBtn != null) {
            int y = optionsBtn.y;
            optionsBtn.setWidth(100);
            optionsBtn.x = centerX - 102;

            ButtonWidget friendsBtn = new ButtonWidget(centerX + 2, y, 100, 20,
                    Text.translatable("button.msf_friends.friends"),
                    b -> msf$openFriends());
            this.addDrawableChild(friendsBtn);
        }

        // Resize LAN button, rename to "多人游戏", and add "举报玩家" button on the right
        if (lanBtn != null) {
            int y = lanBtn.y;
            lanBtn.setWidth(100);
            lanBtn.x = centerX - 102;
            lanBtn.setMessage(Text.translatable("button.msf_friends.multiplayer"));

            MinecraftClient mc = MinecraftClient.getInstance();
            boolean published = mc.getServer() != null && mc.getServer().isRemote();
            ButtonWidget reportBtn = new ButtonWidget(centerX + 2, y, 100, 20,
                    Text.translatable("menu.playerReporting"),
                    b -> mc.setScreen(new SocialInteractionsScreen()));
            reportBtn.active = published;
            this.addDrawableChild(reportBtn);
        }
    }

    @Unique
    private void msf$openFriends() {
        MinecraftClient.getInstance().setScreen(new FriendsScreen(this));
    }
}
