package dev.msf.friends;

import dev.msf.friends.util.Logging;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

@Mod(MsfFriendsConstants.MOD_ID)
public final class MsfFriendsNeoForge {
    private static final Logger LOGGER = Logging.get();
    private static MsfFriendsNeoForge INSTANCE;

    public MsfFriendsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        LOGGER.info("[boot] NeoForge entry point initializing (mod={})", modContainer.getModId());
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("[boot] NeoForge client setup");
        var gameDir   = FMLPaths.GAMEDIR.get();
        var configDir = FMLPaths.CONFIGDIR.get();
        var cacheDir  = gameDir.resolve("libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
        MsfFriendsBoot.start(cacheDir, configDir);
    }

    public static MsfFriendsNeoForge get() { return INSTANCE; }
}
