package dev.msf.friends;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = MsfFriendsForge.MOD_ID, name = "Minecraft Service Friends", version = "@VERSION@",
     clientSideOnly = true, acceptedMinecraftVersions = "[1.11,1.12)")
public class MsfFriendsForge {

    public static final String MOD_ID = "msf_friends";
    private static final Logger LOGGER = LogManager.getLogger("MSF/Friends");

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.11.2 entry point - preInit");
        if (event.getSide() != Side.CLIENT) return;
        File gameDir = event.getModConfigurationDirectory().getParentFile();
        File configDir = event.getModConfigurationDirectory();
        File cacheDir = new File(gameDir, "libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
        MsfFriendsBoot1112.start(cacheDir.toPath(), configDir.toPath());
    }

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.11.2 init - registering key bindings & event handler");
        MsfFriendsKeyBindings1112.register();
        MinecraftForge.EVENT_BUS.register(new FriendsEventHandler());
    }
}
