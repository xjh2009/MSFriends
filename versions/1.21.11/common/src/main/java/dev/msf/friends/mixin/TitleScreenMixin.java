package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
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

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

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
        // Friends screen not yet ported to 1.21.11
    }
}

