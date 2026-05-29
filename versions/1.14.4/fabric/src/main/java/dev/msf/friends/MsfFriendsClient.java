package dev.msf.friends;

import dev.msf.friends.util.Logging;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Fabric entry point for MSF Friends on MC 1.14.4.
 * Registers key bindings and starts the mod.
 */
@Environment(EnvType.CLIENT)
public class MsfFriendsClient implements ClientModInitializer {
    private static final Logger LOGGER = Logging.get();

    @Override
    public void onInitializeClient() {
        Path gameDir = net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath();
        Path webrtcCacheDir = gameDir.resolve(".msf-friends").resolve("webrtc");
        Path configDir = gameDir.resolve(".msf-friends").resolve("config");
        MsfFriendsBoot.start(webrtcCacheDir, configDir);
        LOGGER.info("[client] MSF Friends 1.14.4 client initialized");
    }
}
