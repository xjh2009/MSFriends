package dev.msf.friends;

import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Forge event handler for MC 1.10.2.
 * Replaces the mixin-based approach used in modern versions.
 */
public class ForgeEventHandler {

    private static final Logger LOGGER = Logging.get();
    private static final int FRIENDS_BUTTON_ID = 98765;
    private static final String FRIENDS_BUTTON_TEXT = "Friends";

    private static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends",
            Keyboard.KEY_O,
            "key.categories.msf_friends"
    );

    private boolean registered = false;

    /**
     * Add the "Friends" button to the main menu and pause menu.
     */
    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiMainMenu || event.getGui() instanceof GuiIngameMenu) {
            // Calculate position for the friends button
            int buttonX = event.getGui().width / 2 - 100;
            int buttonY = 0;

            if (event.getGui() instanceof GuiMainMenu) {
                // Main menu: put below other buttons
                buttonY = event.getGui().height / 4 + 108;
                // Shift down slightly if needed
            } else if (event.getGui() instanceof GuiIngameMenu) {
                // Pause menu: put after "Back to Game" button
                buttonY = event.getGui().height / 4 + 104;
            }

            event.getButtonList().add(new GuiButton(FRIENDS_BUTTON_ID, buttonX, buttonY, 200, 20, FRIENDS_BUTTON_TEXT));
        }

        // Register keybinding once
        if (!registered) {
            registered = true;
            // KeyBinding is registered statically in the class, Forge picks it up automatically
            // via the @SubscribeEvent on InputEvent
        }
    }

    /**
     * Handle clicks on the Friends button.
     */
    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.getButton().id == FRIENDS_BUTTON_ID) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new FriendsScreen(event.getGui()));
        }
    }

    /**
     * Open Friends screen with keybind O.
     */
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (OPEN_FRIENDS.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.currentScreen == null) {
                mc.displayGuiScreen(new FriendsScreen(null));
            }
        }
    }

    /**
     * Tick the MSF Friends backend.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MsfFriendsBoot boot = MsfFriendsBoot.get();
            if (boot != null && boot.bridge() != null) {
                // Presence tick is handled by the ScheduledExecutorService in MsfFriendsBoot
                // This tick handler is reserved for future MC-specific tick logic
            }
        }
    }
}
