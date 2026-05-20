package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a 20×20 friends icon button to the title screen, mirroring 26.2's
 * {@code FriendsButton} placement next to the language/accessibility icon buttons.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        // Language/accessibility icon buttons sit at the bottom-right of the
        // title screen.  In vanilla 26.1.2 the language button is at
        // x = width/2 + 106, accessibility at +128.  We add our button at +150,
        // matching the 26.2 FriendsButton placement.
        int x = this.width / 2 + 150;
        int y = this.height / 4 + 48 + 72 + 12;

        SpriteIconButton btn = SpriteIconButton
            .builder(Component.translatable("button.msf_friends.friends"), b -> msf$openFriends(), true)
                .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/friends"), 16, 16)
                .width(20)
                .build();
        btn.setPosition(x, y);
        this.addRenderableWidget(btn);
    }

    @Unique
    private void msf$openFriends() {
        Minecraft.getInstance().setScreen(new FriendsScreen(this));
    }
}

