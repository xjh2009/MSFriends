package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pause screen changes for MC 1.17.1, mirroring 1.18.2's layout.
 */
@Mixin(GameMenuScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Text title) {
        super(title);
    }

    @Redirect(method = "initWidgets", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/integrated/IntegratedServer;isRemote()Z"), require = 0)
    private boolean msf$forceShowLanButton(IntegratedServer server) {
        return false;
    }

    @Inject(method = "initWidgets", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        int centerX = this.width / 2;
        ButtonWidget optionsBtn = null;
        ButtonWidget lanBtn = null;

        for (var child : this.children()) {
            if (child instanceof ButtonWidget btn) {
                Text msg = btn.getMessage();
                String msgStr = msg.getString();
                if (msgStr.equals("Options") || msgStr.equals("选项")) {
                    optionsBtn = btn;
                }
                if (msgStr.contains("LAN") || msgStr.contains("局域网")) {
                    lanBtn = btn;
                }
            }
        }

        if (optionsBtn != null) {
            int y = optionsBtn.y;
            optionsBtn.setWidth(100);
            optionsBtn.x = centerX - 102;

            ButtonWidget friendsBtn = new ButtonWidget(centerX + 2, y, 100, 20,
                    new TranslatableText("button.msf_friends.friends"),
                    b -> msf$openFriends());
            this.addDrawableChild(friendsBtn);

            for (var child : this.children()) {
                if (child instanceof ButtonWidget btn && btn != optionsBtn && btn != lanBtn && btn.y > y) {
                    btn.y += 24;
                }
            }
        }

        if (lanBtn != null) {
            int y = optionsBtn != null ? optionsBtn.y + 24 : lanBtn.y;
            lanBtn.setWidth(100);
            lanBtn.x = centerX - 102;
            lanBtn.y = y;
            lanBtn.setMessage(new TranslatableText("button.msf_friends.multiplayer"));

            MinecraftClient mc = MinecraftClient.getInstance();
            boolean published = mc.getServer() != null && mc.getServer().isRemote();
            ButtonWidget reportBtn = new ButtonWidget(centerX + 2, y, 100, 20,
                    new TranslatableText("menu.playerReporting"),
                    b -> {});
            reportBtn.active = published;
            this.addDrawableChild(reportBtn);
        }
    }

    @Unique
    private void msf$openFriends() {
        MinecraftClient.getInstance().setScreen(new FriendsScreen(this));
    }
}
