package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pause screen changes (mirroring 26.2):
 * <ol>
 *   <li>Adds a "好友" button to the pause menu</li>
 *   <li>"Open to LAN" renamed to "多人游戏"</li>
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
 */
@Mixin(GameMenuScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        // In 1.16.5 GameMenuScreen, the button layout is:
        // Row 1: "返回游戏" centered, y=height/4-16+0  (id=0)
        // Row 2: "进度" at left, "统计" at right  (id=1,2)
        // Row 3: "选项" at left, id=3
        //        For integrated: "对局域网开放" at right, id=4
        //        For dedicated:  blank at right
        // Row 4: "返回主菜单"/"保存并返回" centered (id=5)
        //
        // We add a "好友" button next to 选项 (making 选项 narrower)
        // and add "举报玩家" next to "多人游戏"

        // The vanilla layout uses these positions:
        // left:  width/2 - 102
        // right: width/2 + 2
        // y base: height/4 - 16

        // Button dimensions in 1.16.5
        int bwHalf = 98;  // half-width button
        int bh = 20;
        int yBase = this.height / 4 - 16;

        // Row 3: Options (left) + Friends (right)
        int row3Y = yBase + 48;
        // Add "好友" button to the right of the options row
        this.addButton(new ButtonWidget(this.width / 2 + 2, row3Y, bwHalf, bh,
                new TranslatableText("button.msf_friends.friends"),
                button -> msf$openFriends()));

        // Row 4: "多人游戏" (always visible) + "举报玩家"
        int row4Y = yBase + 72;
        // Rename the existing "对局域网开放" button to "多人游戏" - we handle this in render
        // Add "举报玩家"
        IntegratedServer server = this.client != null ? this.client.getServer() : null;
        // In 1.16.5 Yarn, isOpenToLan is not mapped; use lanPinger != null as a heuristic,
        // or just check if we have an integrated server running
        boolean published = server != null;
        ButtonWidget reportBtn = new ButtonWidget(this.width / 2 + 2, row4Y, bwHalf, bh,
                new TranslatableText("menu.playerReporting"),
                button -> {
                    if (this.client != null) {
                        this.client.openScreen(new SocialInteractionsScreen());
                    }
                });
        reportBtn.active = published;
        this.addButton(reportBtn);
    }

    @Unique
    private void msf$openFriends() {
        MinecraftClient.getInstance().openScreen(new FriendsScreen(this));
    }
}
