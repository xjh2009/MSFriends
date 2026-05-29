package dev.msf.friends;

import dev.msf.friends.util.Logging;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;

@Mod(MsfFriendsConstants.MOD_ID)
public final class MsfFriendsForge {
    private static final Logger LOGGER = Logging.get();

    public MsfFriendsForge() {
        LOGGER.info("[boot] Forge 1.15.2 entry point");
        try {
            initClient();
        } catch (Exception e) {
            LOGGER.error("[boot] Failed to initialize client", e);
        }
    }

    private static void initClient() throws Exception {
        // Use reflection to avoid compile-time dependency on MCP-mapped Minecraft class.
        // Try multiple method names: Yarn (getInstance), SRG (func_71410_x), MCP
        Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
        Object mc = null;
        for (String methodName : new String[]{"getInstance", "func_71410_x", "method_1551"}) {
            try {
                Method m = mcClass.getMethod(methodName);
                mc = m.invoke(null);
                break;
            } catch (NoSuchMethodException ignored) {}
        }
        if (mc == null) throw new RuntimeException("Cannot find Minecraft.getInstance()");
        // Try multiple field names for gameDir: Yarn (gameDir), SRG, MCP
        File gameDir = null;
        for (String fieldName : new String[]{"gameDir", "field_71412_D", "field_71412_d", "runDirectory"}) {
            try {
                java.lang.reflect.Field f = mcClass.getField(fieldName);
                gameDir = (File) f.get(mc);
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        if (gameDir == null) throw new RuntimeException("Cannot find Minecraft.gameDir");
        Path gamePath = gameDir.toPath();
        Path configPath = gamePath.resolve("config");
        Path cachePath = gamePath.resolve("libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
        MsfFriendsBoot.start(cachePath, configPath);
    }
}
