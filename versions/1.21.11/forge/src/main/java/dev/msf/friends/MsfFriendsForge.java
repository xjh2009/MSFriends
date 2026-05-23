package dev.msf.friends;

import dev.msf.friends.util.Logging;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MsfFriendsConstants.MOD_ID)
public final class MsfFriendsForge {
    private static final Logger LOGGER = Logging.get();

    public MsfFriendsForge() {
        LOGGER.info("[boot] Forge entry point");
        var gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        var configDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        var cacheDir = gameDir.resolve("libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
        MsfFriendsBoot.start(cacheDir, configDir);
    }
}