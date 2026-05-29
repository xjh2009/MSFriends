package dev.msf.friends;

import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.util.Logging;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.slf4j.Logger;

import java.io.File;

/**
 * Forge 1.8.8 entry point.
 * Uses FML {@code net.minecraftforge.fml} packages (1.8.9 repackaging).
 */
@Mod(modid = MsfFriendsConstants.MOD_ID, name = "MSF Friends", version = "1.0.0",
     acceptedMinecraftVersions = "[1.8.8,1.8.9]")
public final class MsfFriendsForge {
    private static final Logger LOGGER = Logging.get();

    /** Key binding: O — opens the friends screen while in-game. */
    public static final KeyBinding OPEN_FRIENDS = new KeyBinding(
            "key.msf_friends.open_friends", Keyboard.KEY_O, "key.categories.msf_friends.category");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.8.8 entry point — preInit");
        File configDir = event.getModConfigurationDirectory();
        File gameDir = configDir.getParentFile();
        File cacheDir = new File(gameDir, "libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
        MsfFriendsBoot.start(cacheDir.toPath(), configDir.toPath());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.8.8 entry point — init");
        ClientRegistry.registerKeyBinding(OPEN_FRIENDS);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (OPEN_FRIENDS.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.theWorld != null) {
                mc.displayGuiScreen(new FriendsScreen(mc.currentScreen));
            }
        }
    }
}
