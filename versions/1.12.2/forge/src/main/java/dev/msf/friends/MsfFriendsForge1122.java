package dev.msf.friends;

import dev.msf.friends.event.ForgeEventHandler;
import dev.msf.friends.bridge.HeadlessMinecraftBridge1122;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = MsfFriendsConstants.MOD_ID,
     name = "MSF Friends",
     version = MsfFriendsConstants.VERSION,
     clientSideOnly = true,
     acceptedMinecraftVersions = "[1.12.2]")
public class MsfFriendsForge1122 {
    private static final Logger LOGGER = LogManager.getLogger(MsfFriendsConstants.LOGGER_NAME);

    @Mod.Instance
    public static MsfFriendsForge1122 INSTANCE;

    private File configDir;
    private File gameDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.12.2 entry point - preInit");
        this.gameDir = event.getModConfigurationDirectory().getParentFile();
        this.configDir = event.getModConfigurationDirectory();
        MsfFriendsBoot1122.bootDir = gameDir.toPath();
        MsfFriendsBoot1122.configDir = configDir.toPath();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.12.2 entry point - init");
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());
        MsfFriendsBoot1122.start();
    }
}
