package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.screen.IconButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a friends button to the title screen for MC 1.17.1.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique private static final Identifier MSF$FRIENDS_ICON = new Identifier("msf_friends", "textures/gui/sprites/icon/friends.png");

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) {
            return;
        }

        int x = this.width / 2 + 128;
        int y = this.height / 4 + 48 + 72 + 12;

        IconButtonWidget btn = new IconButtonWidget(x, y, 20, 20,
                new TranslatableText("button.msf_friends.friends"), b -> msf$openFriends(), MSF$FRIENDS_ICON, 16, 16);
        this.addDrawableChild(btn);
    }

    @Unique
    private void msf$openFriends() {
        MinecraftClient.getInstance().setScreen(new FriendsScreen(this));
    }
}
