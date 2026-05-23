package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a friends button to the title screen, mirroring 26.2's placement.
 * 1.18.2 has no SpriteIconButton, so we use a plain Button with TranslatableComponent.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        // Place next to language/accessibility icon buttons (bottom-right area).
        // In vanilla 1.18.2, language button is at width/2+104, accessibility at width/2+126.
        // We add our button at width/2+148, matching 26.2's relative position.
        int x = this.width / 2 + 148;
        int y = this.height / 4 + 48 + 72 + 12;

        this.addRenderableWidget(new Button(x, y, 20, 20,
                new TranslatableComponent("button.msf_friends.friends"),
                b -> msf$openFriends()));
    }

    @Unique
    private void msf$openFriends() {
        Minecraft.getInstance().setScreen(new FriendsScreen((Screen)(Object)this));
    }
}
