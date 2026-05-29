package dev.msf.friends.event;

import dev.msf.friends.MsfFriendsBoot1122;
import dev.msf.friends.MsfFriendsConstants;
import dev.msf.friends.gui.FriendsGuiScreen;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * Forge event handler replacing all 16 Mixin classes from 1.19.2.
 * Handles: title screen, pause screen, key bindings, tick events, server lifecycle.
 */
@SideOnly(Side.CLIENT)
public class ForgeEventHandler {
    private static final Logger LOGGER = Logging.get();
    private static final int FRIENDS_BUTTON_ID = 9001;
    private static final int PAUSE_FRIENDS_BUTTON_ID = 9002;
    private static final int PAUSE_MULTIPLAYER_BUTTON_ID = 9003;

    public static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends",
            Keyboard.KEY_O,
            "key.categories.msf_friends.category"
    );

    private boolean registeredKeyBinding = false;
    private boolean isInP2PMode = false;
    private String originalTitle = null;

    public ForgeEventHandler() {
        ClientRegistry.registerKeyBinding(OPEN_FRIENDS);
        LOGGER.info("[event] Forge event handler registered, key binding O registered");
    }

    // --- Key binding: Open friends screen ---
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (OPEN_FRIENDS.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.world != null) {
                mc.displayGuiScreen(new FriendsGuiScreen());
            }
        }
    }

    // --- Title screen: add friends button ---
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (gui instanceof GuiMainMenu) {
            // We'll add the button in PostInitAction via GuiScreenEvent
            LOGGER.debug("[event] Title screen opened");
        }
    }

    // --- GuiScreen Init: inject buttons into title/pause screens ---
    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();

        if (gui instanceof GuiMainMenu) {
            // Add friends button to title screen
            event.getButtonList().add(new GuiButton(
                    FRIENDS_BUTTON_ID,
                    gui.width / 2 - 100 - 104, // left of Realms button
                    gui.height / 4 + 48 + 24,
                    20, 20,
                    "F" // Will be rendered as icon
            ));
            LOGGER.debug("[event] Added friends button to title screen");
        }

        if (gui instanceof GuiIngameMenu) {
            // Add friends button to pause screen
            event.getButtonList().add(new GuiButton(
                    PAUSE_FRIENDS_BUTTON_ID,
                    gui.width / 2 + 4,
                    gui.height / 4 + 72 + 12,
                    98, 20,
                    "Friends"
            ));

            // Check if we're hosting P2P - change "Open to LAN" button text
            MsfFriendsBoot1122 boot = MsfFriendsBoot1122.get();
            if (boot != null && boot.bridge() != null) {
                MinecraftBridge bridge = boot.bridge();
                if (bridge.isHostingP2P()) {
                    // Modify the "Open to LAN" button to say "Online Settings"
                    for (GuiButton btn : event.getButtonList()) {
                        if (btn.displayString != null && btn.displayString.contains("LAN")) {
                            btn.displayString = "Online Settings";
                            break;
                        }
                    }
                }
            }
            LOGGER.debug("[event] Added friends button to pause screen");
        }
    }

    // --- Button click handler ---
    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        GuiScreen gui = event.getGui();
        GuiButton button = event.getButton();

        if (button.id == FRIENDS_BUTTON_ID && gui instanceof GuiMainMenu) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new FriendsGuiScreen());
        }

        if (button.id == PAUSE_FRIENDS_BUTTON_ID && gui instanceof GuiIngameMenu) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new FriendsGuiScreen());
        }

        // Handle "Open to LAN" / "Online Settings" button in pause screen
        if (gui instanceof GuiIngameMenu && button.id == 7) { // Share to LAN button id
            MsfFriendsBoot1122 boot = MsfFriendsBoot1122.get();
            if (boot != null && boot.bridge() != null) {
                MinecraftBridge bridge = boot.bridge();
                if (bridge.multiplayerScope() == MinecraftBridge.MultiplayerScope.OFF) {
                    bridge.setMultiplayerScope(MinecraftBridge.MultiplayerScope.LAN);
                } else if (bridge.multiplayerScope() == MinecraftBridge.MultiplayerScope.LAN) {
                    bridge.setMultiplayerScope(MinecraftBridge.MultiplayerScope.ONLINE);
                    MsfFriendsBoot1122.get().p2p().onHostScopeChanged(MinecraftBridge.MultiplayerScope.ONLINE);
                } else {
                    bridge.setMultiplayerScope(MinecraftBridge.MultiplayerScope.OFF);
                    MsfFriendsBoot1122.get().p2p().onHostScopeChanged(MinecraftBridge.MultiplayerScope.OFF);
                }
            }
        }
    }

    // --- Client tick: update window title for P2P hosting ---
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        MsfFriendsBoot1122 boot = MsfFriendsBoot1122.get();
        if (boot == null || boot.bridge() == null) return;

        MinecraftBridge bridge = boot.bridge();
        boolean nowP2P = bridge.isHostingP2P();

        if (nowP2P != isInP2PMode) {
            isInP2PMode = nowP2P;
            try {
                if (nowP2P) {
                    originalTitle = org.lwjgl.opengl.Display.getTitle();
                    org.lwjgl.opengl.Display.setTitle("Minecraft - Online");
                } else if (originalTitle != null) {
                    org.lwjgl.opengl.Display.setTitle(originalTitle);
                    originalTitle = null;
                }
            } catch (Throwable t) {
                LOGGER.debug("[event] Could not change window title", t);
            }
        }
    }

    // --- Player joins world: set up initial state ---
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote && event.getEntity() == Minecraft.getMinecraft().player) {
            LOGGER.debug("[event] Local player joined world");
        }
    }
}
