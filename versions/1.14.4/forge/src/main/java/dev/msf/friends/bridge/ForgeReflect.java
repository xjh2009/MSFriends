package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reflection helper for MC 1.13.2 Forge.
 *
 * <p>ForgeGradle 7 decompiles MC to MCP-mapped names at compile time.  At runtime,
 * the dev environment has remapped classes on the classpath, so MCP names work
 * directly.  For production builds, SRG names are used — the refmap translates
 * mixin targets, and this class provides SRG fallback for reflective access.
 *
 * <p>Strategy: try MCP (human-readable) name first, then SRG name.
 */
public final class ForgeReflect {
    private static final Logger LOGGER = Logging.get();

    private static final Map<String, String> CLASS_MAP = Map.ofEntries(
            Map.entry("net.minecraft.client.Minecraft",                               "net.minecraft.client.Minecraft"),
            Map.entry("net.minecraft.client.gui.screen.Screen",                           "net.minecraft.client.gui.Screen"),
            Map.entry("net.minecraft.client.gui.screen.MainMenuScreen",                         "net.minecraft.client.gui.MainMenuScreen"),
            Map.entry("net.minecraft.client.gui.screen.IngameMenuScreen",                       "net.minecraft.client.gui.IngameMenuScreen"),
            Map.entry("net.minecraft.client.gui.screen.ShareToLanScreen",                       "net.minecraft.client.gui.ShareToLanScreen"),
            Map.entry("net.minecraft.client.gui.widget.button.Button",                           "net.minecraft.client.gui.Button"),
            Map.entry("net.minecraft.client.gui.widget.TextFieldWidget",                        "net.minecraft.client.gui.TextFieldWidget"),
            Map.entry("net.minecraft.network.NetworkManager",                         "net.minecraft.network.NetworkManager"),
            Map.entry("net.minecraft.network.login.client.CLoginStartPacket",         "net.minecraft.network.login.client.CLoginStartPacket"),
            Map.entry("net.minecraft.client.network.login.ClientLoginNetHandler",           "net.minecraft.client.network.ClientLoginNetHandler"),
            Map.entry("net.minecraft.network.login.ServerLoginNetHandler",                  "net.minecraft.network.ServerLoginNetHandler"),
            Map.entry("net.minecraft.server.integrated.IntegratedServer",             "net.minecraft.server.integrated.IntegratedServer"),
            Map.entry("net.minecraft.server.MinecraftServer",                         "net.minecraft.server.MinecraftServer"),
            Map.entry("net.minecraft.client.GameSettings",                            "net.minecraft.client.GameSettings"),
            Map.entry("net.minecraft.client.settings.KeyBinding",                     "net.minecraft.client.settings.KeyBinding")
    );

    // SRG method name fallbacks for 1.13.2
    private static final Map<String, String> METHOD_MAP = Map.ofEntries(
            Map.entry("net.minecraft.client.Minecraft.getInstance",                    "func_71410_x"),
            Map.entry("net.minecraft.client.Minecraft.displayGuiScreen",               "func_147108_a"),
            Map.entry("net.minecraft.client.Minecraft.getIntegratedServer",            "func_71401_C"),
            Map.entry("net.minecraft.network.NetworkManager.sendPacket",               "func_179290_a"),
            Map.entry("net.minecraft.network.ServerLoginNetHandler.tryAcceptPlayer",   "func_147326_c"),
            Map.entry("net.minecraft.server.integrated.IntegratedServer.shareToLAN",   "func_71206_a"),
            Map.entry("net.minecraft.server.MinecraftServer.getPlayerList",            "func_184103_al")
    );

    // SRG field name fallbacks for 1.13.2
    private static final Map<String, String> FIELD_MAP = Map.ofEntries(
            Map.entry("net.minecraft.client.Minecraft.world",                          "field_71441_e"),
            Map.entry("net.minecraft.client.Minecraft.currentScreen",                  "field_71462_r"),
            Map.entry("net.minecraft.client.Minecraft.session",                        "field_71449_j"),
            Map.entry("net.minecraft.client.Minecraft.player",                         "field_71439_g"),
            Map.entry("net.minecraft.client.Minecraft.fontRenderer",                   "field_71466_p"),
            Map.entry("net.minecraft.client.GameSettings.keyBindings",                 "field_74324_K"),
            Map.entry("net.minecraft.network.NetworkManager.channel",                  "field_150746_k"),
            Map.entry("net.minecraft.network.NetworkManager.socketAddress",            "field_74459_b"),
            Map.entry("net.minecraft.network.ServerLoginNetHandler.networkManager",    "field_147333_a"),
            Map.entry("net.minecraft.network.ServerLoginNetHandler.server",            "field_147334_b"),
            Map.entry("net.minecraft.network.ServerLoginNetHandler.loginGameProfile",  "field_147337_i")
    );

    private ForgeReflect() {}

    public static Class<?> mcClass(String mcpName) throws ClassNotFoundException {
        try {
            return Class.forName(mcpName);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("[reflect] MCP class not found: {}, trying SRG lookup", mcpName);
            throw e;
        }
    }

    public static Method mcMethod(Class<?> clazz, String mcpMethod, Class<?>... paramTypes)
            throws NoSuchMethodException {
        // Try MCP name first
        try { return clazz.getMethod(mcpMethod, paramTypes); } catch (NoSuchMethodException ignored) {}
        // Fall back to SRG name via mapping
        for (String classKey : findAllClassKeys(clazz)) {
            String key = classKey + "." + mcpMethod;
            String srg = METHOD_MAP.get(key);
            if (srg != null) {
                try { return clazz.getMethod(srg, paramTypes); } catch (NoSuchMethodException ignored) {}
            }
        }
        // Try common SRG patterns as last resort
        for (String classKey : findAllClassKeys(clazz)) {
            String key = classKey + "." + mcpMethod;
            String srg = METHOD_MAP.get(key);
            if (srg != null) {
                try { return clazz.getDeclaredMethod(srg, paramTypes); } catch (NoSuchMethodException ignored2) {}
            }
        }
        throw new NoSuchMethodException("Cannot find method " + mcpMethod +
                " in " + clazz.getName() + " (tried keys: " + findAllClassKeys(clazz) + ")");
    }

    public static Field mcField(Class<?> clazz, String mcpField) throws NoSuchFieldException {
        // Try MCP name first
        try { return clazz.getDeclaredField(mcpField); } catch (NoSuchFieldException ignored) {}
        // Fall back to SRG name via mapping
        for (String classKey : findAllClassKeys(clazz)) {
            String key = classKey + "." + mcpField;
            String srg = FIELD_MAP.get(key);
            if (srg != null) {
                try { return clazz.getDeclaredField(srg); } catch (NoSuchFieldException ignored) {}
            }
        }
        throw new NoSuchFieldException("Cannot find field " + mcpField +
                " in " + clazz.getName() + " (tried keys: " + findAllClassKeys(clazz) + ")");
    }

    private static List<String> findAllClassKeys(Class<?> clazz) {
        String name = clazz.getName();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, String> e : CLASS_MAP.entrySet()) {
            if (e.getValue().equals(name)) keys.add(e.getKey());
        }
        if (keys.isEmpty()) keys.add(name);
        return keys;
    }
}
