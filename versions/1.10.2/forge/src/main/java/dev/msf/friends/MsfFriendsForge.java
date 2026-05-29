package dev.msf.friends;

import dev.msf.friends.util.Logging;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Forge 1.10.2 mod entry point.
 */
@Mod(modid = MsfFriendsConstants.MOD_ID, name = "MSF Friends", version = "0.1.0")
public class MsfFriendsForge {

    private static final Logger LOGGER = Logging.get();

    public MsfFriendsForge() {
        LOGGER.info("[boot] MSF Friends Forge 1.10.2 constructor");
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[boot] MSF Friends Forge 1.10.2 preInit");
        // Register event handlers
        MinecraftForge.EVENT_BUS.register(new dev.msf.friends.ForgeEventHandler());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[boot] MSF Friends Forge 1.10.2 init");
        try {
            File gameDir = net.minecraftforge.fml.common.Loader.instance().getConfigDir().getParentFile();
            File configDir = net.minecraftforge.fml.common.Loader.instance().getConfigDir();
            File cacheDir = new File(gameDir, "libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
            MsfFriendsBoot.start(cacheDir.toPath(), configDir.toPath());
        } catch (Throwable t) {
            LOGGER.error("[boot] Failed to start MSF Friends", t);
        }
    }
}
