package dev.msf.friends;

import dev.msf.friends.FriendsScreen1112;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * Forge event handler for MSF Friends 1.11.2.
 * Injects the "Friends" button into title and pause screens, and handles key bindings.
 */
@SideOnly(Side.CLIENT)
public class FriendsEventHandler {

    private static final Logger LOGGER = Logging1112.get();
    private static final int FRIENDS_BUTTON_ID = 250;

    /**
     * Inject "Friends" button into the title screen.
     */
    @SubscribeEvent
    public void onGuiInitTitle(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiMainMenu) {
            int cx = event.getGui().width / 2;
            event.getButtonList().add(new GuiButton(
                    FRIENDS_BUTTON_ID, cx - 100, event.getGui().height / 4 + 108, 98, 20, "Friends"));
        } else if (event.getGui() instanceof GuiIngameMenu) {
            // Pause screen - add friends button near bottom
            event.getButtonList().add(new GuiButton(
                    FRIENDS_BUTTON_ID, 4, event.getGui().height - 24, 80, 20, "Friends"));
        }
    }

    /**
     * Handle Friends button click on title/pause screen.
     */
    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.getButton().id == FRIENDS_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(
                    new FriendsScreen1112(event.getGui()));
        }
    }

    /**
     * Handle the F key binding to open friends screen at any time.
     */
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (MsfFriendsKeyBindings1112.OPEN_FRIENDS.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new FriendsScreen1112(null));
            }
        }
    }
}
