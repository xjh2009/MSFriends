package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin() {
        super(new LiteralText("dummy"));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) return;

        int x = this.width / 2 - 102;
        int y = this.height / 4 + 72 + -16;
        this.addButton(new ButtonWidget(x, y, 204, 20, "Friends",
                btn -> MinecraftClient.getInstance().openScreen(new FriendsScreen(this))));
    }
}
