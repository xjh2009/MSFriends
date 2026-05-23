package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.screen.IconButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a 20×20 friends icon button to the title screen, mirroring 26.1.2's
 * placement next to the language/accessibility icon buttons.
 *
 * In 1.20.1 Fabric the language button sits at x = width/2 + 104.
 * We place our button at width/2 + 128 (same spacing as 26.1.2).
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        // In 1.20.1 Fabric TitleScreen, language button is at width/2 + 104, y = height/4 + 48 + 72 + 12
        // We add our icon button at width/2 + 128 (just to the right of language)
        int x = this.width / 2 + 128;
        int y = this.height / 4 + 48 + 72 + 12;

        this.addDrawableChild(
            IconButtonWidget.builder(
                Text.translatable("button.msf_friends.friends"),
                b -> msf$openFriends(),
                new Identifier("msf_friends", "textures/gui/sprites/icon/friends.png"),
                16, 16
            ).dimensions(x, y, 20, 20).build()
        );
    }

    @Unique
    private void msf$openFriends() {
        MinecraftClient.getInstance().setScreen(new FriendsScreen(this));
    }
}
