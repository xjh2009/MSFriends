package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() {
        super(new LiteralText("dummy"));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) return;

        this.addButton(new ButtonWidget(this.width / 2 - 100, this.height / 4 + 48 + 24 * 2, 200, 20, "Friends",
                btn -> MinecraftClient.getInstance().openScreen(new FriendsScreen(this))));
    }
}
