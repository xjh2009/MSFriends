package dev.msf.friends.event;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.MsfFriendsConstants;
import dev.msf.friends.screen.FriendsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.apache.logging.log4j.Logger;

/**
 * Forge event handler for MC 1.9.4.
 * Replaces TitleScreenMixin, PauseScreenMixin, and KeyBindingMixin.
 *
 * Uses Forge events:
 * - InitGuiEvent.Post to inject "Friends" buttons into title/pause screens
 * - ActionPerformedEvent.Post to handle button clicks
 * - KeyInputEvent for the "O" key binding
 */
public final class FriendsEventHandler {
    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;

    /** Button ID for the Friends button — chosen to avoid collision with vanilla IDs (0-7). */
    public static final int FRIENDS_BUTTON_ID = 301;

    private static final String FRIENDS_BUTTON_TEXT = "Friends";
    private static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends",
            Keyboard.KEY_O,
            "MSF Friends"
    );

    private static boolean registered = false;

    private FriendsEventHandler() {}

    public static void register() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new FriendsEventHandler());
            ClientRegistry.registerKeyBinding(OPEN_FRIENDS);
            registered = true;
            LOGGER.info("[event] FriendsEventHandler registered");
        }
    }

    // ========== Key Binding ==========

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (OPEN_FRIENDS.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.theWorld != null) {
                mc.displayGuiScreen(new FriendsScreen(mc.currentScreen));
            }
        }
    }

    // ========== Title Screen Button Injection ==========

    @SubscribeEvent
    public void onTitleScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiMainMenu)) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int scaledWidth = sr.getScaledWidth();
        int scaledHeight = sr.getScaledHeight();

        // Add "Friends" button to the left of the title screen
        int btnX = scaledWidth / 2 - 100;
        int btnY = scaledHeight / 4 + 120 + 12;

        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot != null) {
            event.getButtonList().add(new GuiButton(FRIENDS_BUTTON_ID, btnX, btnY, 200, 20, FRIENDS_BUTTON_TEXT));
        }
    }

    // ========== Pause Screen Button Injection ==========

    @SubscribeEvent
    public void onPauseScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiIngameMenu)) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int scaledWidth = sr.getScaledWidth();
        int scaledHeight = sr.getScaledHeight();

        int btnX = scaledWidth / 2 - 100;
        int btnY = scaledHeight / 4 + 120 + 24;

        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot != null) {
            event.getButtonList().add(new GuiButton(FRIENDS_BUTTON_ID, btnX, btnY, 200, 20, FRIENDS_BUTTON_TEXT));
        }
    }

    // ========== Button Click Handling ==========

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.getButton().id == FRIENDS_BUTTON_ID) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.displayGuiScreen(new FriendsScreen(event.getGui()));
            }
        }
    }
}
