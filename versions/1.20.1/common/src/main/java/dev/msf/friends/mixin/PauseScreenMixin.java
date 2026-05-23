package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Pause screen changes (mirroring 26.1.2):
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
 *
 * 1.20.1 Yarn: PauseScreen → GameMenuScreen, GridLayout → GridWidget,
 * RowHelper → Adder, LayoutElement → Widget, isPublished() same name.
 */
@Mixin(GameMenuScreen.class)
public abstract class PauseScreenMixin extends Screen {

    @Unique private GridWidget.Adder msf$adder;
    @Unique private int msf$addChildCount;

    protected PauseScreenMixin(Text title) {
        super(title);
    }

    /**
     * Force showing the "Open to LAN" button even after publishing.
     */
    @Redirect(method = "initWidgets",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/server/integrated/IntegratedServer;isRemote()Z"))
    private boolean msf$forceShowLanButton(IntegratedServer server) {
        return false;
    }

    /**
     * Capture the Adder when it's created.
     */
    @Redirect(method = "initWidgets",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/widget/GridWidget;createAdder(I)Lnet/minecraft/client/gui/widget/GridWidget$Adder;"))
    private GridWidget.Adder msf$captureAdder(GridWidget grid, int columns) {
        this.msf$adder = grid.createAdder(columns);
        this.msf$addChildCount = 0;
        return this.msf$adder;
    }

    /**
     * Intercept add calls to:
     * - Make OPTIONS half-width and add "好友" after it
     * - Add "举报玩家" after SHARE_TO_LAN
     */
    @Redirect(method = "initWidgets",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/widget/GridWidget$Adder;add(Lnet/minecraft/client/gui/widget/Widget;)Lnet/minecraft/client/gui/widget/Widget;"))
    private Widget msf$interceptAdd(GridWidget.Adder adder, Widget element) {
        msf$addChildCount++;

        // Count: 1=RETURN_TO_GAME(span2), 2=ADVANCEMENTS, 3=STATS, 4=OPTIONS, 5=SHARE_TO_LAN, (6=PLAYER_REPORTING vanilla only if published), 7=RETURN/SAVE(span2)
        // But RETURN_TO_GAME is added with add(Widget, 2) not add(Widget), so it's NOT intercepted here.
        // Actual intercepted: 1=ADVANCEMENTS, 2=STATS, 3=OPTIONS, 4=SHARE_TO_LAN, ...

        if (msf$addChildCount == 3 && element instanceof ButtonWidget btn) {
            // OPTIONS button - make it half-width by changing its width directly
            btn.setWidth(98);
            Widget result = adder.add(btn);

            // Add "好友" button next to it
            if (MsfFriendsBoot.get() != null) {
                ButtonWidget friendsBtn = ButtonWidget.builder(
                        Text.translatable("button.msf_friends.friends"),
                        b -> msf$openFriends()
                ).width(98).build();
                adder.add(friendsBtn);
            }
            return result;
        }

        if (msf$addChildCount == 4 && element instanceof ButtonWidget btn) {
            // SHARE_TO_LAN button - rename and add to row
            btn.setMessage(Text.translatable("button.msf_friends.multiplayer"));
            Widget result = adder.add(element);

            // Add "举报玩家" button next to it
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean published = mc.getServer() != null
                    && mc.getServer().isRemote();

            ButtonWidget reportBtn = ButtonWidget.builder(
                    Text.translatable("menu.playerReporting"),
                    b -> mc.setScreen(new SocialInteractionsScreen())
            ).width(98).build();
            reportBtn.active = published;
            adder.add(reportBtn);

            return result;
        }

        // Default: pass through
        return adder.add(element);
    }

    @Unique
    private void msf$openFriends() {
        MinecraftClient.getInstance().setScreen(new FriendsScreen(this));
    }
}
