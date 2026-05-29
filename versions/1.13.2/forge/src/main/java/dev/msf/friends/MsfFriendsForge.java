package dev.msf.friends;

import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;

@Mod(MsfFriendsConstants.MOD_ID)
public final class MsfFriendsForge {
    private static final Logger LOGGER = Logging.get();

    public MsfFriendsForge() {
        LOGGER.info("[boot] Forge 1.13.2 entry point");
        DistExecutor.runWhenOn(Dist.CLIENT, () -> MsfFriendsForge::initClient);
    }

    private static void initClient() {
        File gameDir = Minecraft.getInstance().gameDir;
        Path gamePath = gameDir.toPath();
        Path configPath = gamePath.resolve("config");
        Path cachePath = gamePath.resolve("libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
        MsfFriendsBoot.start(cachePath, configPath);
    }
}
