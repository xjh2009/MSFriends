package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a friends button to the title screen for MC 1.19.2.
 * 1.19.2 does not have SpriteIconButton, so we use a regular ButtonWidget instead.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) {
            return;
        }

        int centerX = this.width / 2;
        // 1.19.2 TitleScreen bottom row (y = height/4 + 48 + 72 + 12) has:
        //   Language(20x20) at width/2-124
        //   Options(98x20)  at width/2-100
        //   Quit(98x20)     at width/2+2
        //   Accessibility(20x20) at width/2+104
        // Place Friends button on a new row BELOW the bottom row (+24)
        int bottomRowY = this.height / 4 + 48 + 72 + 12;
        int y = bottomRowY + 24;

        // Use 98px width to match Options/Quit style, positioned like Options
        ButtonWidget btn = new ButtonWidget(centerX - 100, y, 200, 20,
                Text.translatable("button.msf_friends.friends"),
                b -> msf$openFriends());
        this.addDrawableChild(btn);
    }

    @Unique
    private void msf$openFriends() {
        MinecraftClient.getInstance().setScreen(new FriendsScreen(this));
    }
}
