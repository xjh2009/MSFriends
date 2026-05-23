package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a 20×20 friends icon button to the title screen, mirroring 26.2's
 * FriendsButton placement next to the language/accessibility icon buttons.
 *
 * In 1.16.5 the language button is at x = width/2 + 104, accessibility at +128.
 * We add our button at +152.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        // In 1.16.5 TitleScreen, the language button sits at
        // x = this.width / 2 + 104, y = this.height / 4 + 48 + 72 + 12
        // Accessibility button at x = this.width / 2 + 128
        // We place the friends button at +152
        int x = this.width / 2 + 152;
        int y = this.height / 4 + 48 + 72 + 12;

        this.addButton(new ButtonWidget(x, y, 20, 20,
                new TranslatableText("button.msf_friends.friends"),
                button -> msf$openFriends()));
    }

    @Unique
    private void msf$openFriends() {
        MinecraftClient.getInstance().openScreen(new FriendsScreen(this));
    }
}
