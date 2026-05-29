package dev.msf.friends.mixin;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.screen.IconButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.util.text.StringTextComponent;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a friends button to the title screen for MC 1.13.2.
 * 1.13.2 MCP: MainMenuScreen (not MainMenuScreen), Screen (not Screen),
 * init() (not init()), addButton(T).
 */
@Mixin(MainMenuScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin() { super(new net.minecraft.util.text.StringTextComponent("Title")); }

    @Unique
    private static final ResourceLocation MSF$FRIENDS_ICON =
            new ResourceLocation("msf_friends", "textures/gui/sprites/icon/friends.png");

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsButton(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) {
            return;
        }

        // 1.13.2 MainMenuScreen bottom row layout:
        //   Language(20x20) at width/2-124
        //   Options(98x20)  at width/2-100
        //   Quit(98x20)     at width/2+2
        //   Accessibility(20x20) at width/2+104
        int x = this.width / 2 + 128;
        int y = this.height / 4 + 48 + 72 + 12;

        IconButtonWidget btn = new IconButtonWidget(x, y, 20, 20,
                net.minecraft.client.resources.I18n.format("button.msf_friends.friends"),
                b -> msf$openFriends(), MSF$FRIENDS_ICON, 16, 16);
        this.addButton(btn);
    }

    @Unique
    private void msf$openFriends() {
        Minecraft.getInstance().displayGuiScreen(new FriendsScreen(this));
    }
}
