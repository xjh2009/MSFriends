package dev.msf.friends;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Forge 1.10 entry point.
 *
 * <p>Forge 1.10 (FML 2.x) uses {@code @Mod(modid=…)} with parameters
 * and {@code @Mod.EventHandler} methods instead of constructor injection.
 * {@code FMLPaths} does not exist — directories come from the event
 * or {@code Loader.instance()}.
 *
 * <p>Key binding handling is done via Forge events rather than Mixin
 * injection into {@code Minecraft.runTick()}.
 */
@Mod(modid = MsfFriendsConstants.MOD_ID,
     name = MsfFriendsConstants.MOD_NAME,
     version = "0.1.0",
     acceptedMinecraftVersions = "[1.10,1.11)")
public final class MsfFriendsForge {
    private static final Logger LOGGER = LogManager.getLogger(MsfFriendsConstants.MOD_ID);

    private static boolean initialized = false;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.10 preInit");
        if (event.getSide() == Side.CLIENT) {
            MsfKeyBindings.register();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.10 init");
        MinecraftForge.EVENT_BUS.register(this);

        if (!initialized) {
            initialized = true;
            File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
            File gameDir = Minecraft.getMinecraft().mcDataDir;
            File cacheDir = new File(gameDir, "libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
            LOGGER.info("[boot] configDir = " + configDir);
            LOGGER.info("[boot] gameDir   = " + gameDir);
            LOGGER.info("[boot] cacheDir  = " + cacheDir);

            // Bootstrap the friends service
            // For 1.10 the full friends stack requires Java 17+ common module.
            // This entry point registers the mod so Forge loads it; the
            // advanced features (P2P, WebRTC, friends UI) are structured here
            // but the full common module integration is pending a Java 8 port.
            LOGGER.info("[boot] MSF Friends mod registered (features pending Java 8 common module)");
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("[boot] Forge 1.10 postInit complete");
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;

        // Check key binding
        while (MsfKeyBindings.OPEN_FRIENDS.isPressed()) {
            LOGGER.debug("[input] Friends key pressed — GUI not yet ported");
            // TODO: mc.displayGuiScreen(new FriendsScreen(mc.currentScreen));
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        LOGGER.debug("[network] Connected to server");
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        LOGGER.debug("[network] Disconnected from server");
    }
}
