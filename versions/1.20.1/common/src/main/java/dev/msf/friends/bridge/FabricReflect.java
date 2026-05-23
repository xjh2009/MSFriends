package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Centralized reflection helper that resolves Yarn-mapped names to their
 * runtime equivalents. On Fabric 1.20.1 with Yarn mappings, the dev
 * environment uses Yarn names (e.g. {@code MinecraftClient}) but the
 * production runtime uses intermediary names (e.g. {@code class_310}).
 *
 * <p>All reflective access to Minecraft classes, methods, and fields must go
 * through this class so that the correct names are used at runtime.
 *
 * <p>Coverage mirrors 26.1.2's direct class references. All MC classes used
 * by the bridge layer, screen layer, and mixin targets are mapped here.
 */
public final class FabricReflect {
    private static final Logger LOGGER = Logging.get();

    // ---------------------------------------------------------------------
    // Class name mappings: Yarn → Intermediary
    // ---------------------------------------------------------------------
    private static final Map<String, String> CLASS_MAP = Map.ofEntries(
            // client
            Map.entry("net.minecraft.client.MinecraftClient",                      "net.minecraft.class_310"),
            Map.entry("net.minecraft.client.util.Session",                         "net.minecraft.class_320"),
            Map.entry("net.minecraft.client.gui.screen.Screen",                    "net.minecraft.class_437"),
            Map.entry("net.minecraft.client.gui.hud.DebugHud",                     "net.minecraft.class_340"),
            Map.entry("net.minecraft.client.network.ClientLoginNetworkHandler",    "net.minecraft.class_635"),
            Map.entry("net.minecraft.client.network.ServerInfo",                   "net.minecraft.class_642"),
            Map.entry("net.minecraft.client.network.ServerInfo$ServerType",        "net.minecraft.class_642$class_8678"),
            // network
            Map.entry("net.minecraft.network.ClientConnection",                    "net.minecraft.class_2535"),
            Map.entry("net.minecraft.network.NetworkSide",                         "net.minecraft.class_2598"),
            Map.entry("net.minecraft.network.PacketListener",                      "net.minecraft.class_2547"),
            Map.entry("net.minecraft.network.packet.Packet",                       "net.minecraft.class_2596"),
            Map.entry("net.minecraft.network.packet.login.LoginHelloC2SPacket",    "net.minecraft.class_2915"),
            // server
            Map.entry("net.minecraft.server.ServerNetworkIo",                      "net.minecraft.class_3242"),
            Map.entry("net.minecraft.server.network.ServerHandshakeNetworkHandler","net.minecraft.class_3246"),
            // login
            Map.entry("net.minecraft.client.network.ClientLoginNetworkHandler",    "net.minecraft.class_635"),
            // player
            Map.entry("net.minecraft.client.network.ClientPlayNetworkHandler",     "net.minecraft.class_634"),
            // screens
            Map.entry("net.minecraft.client.gui.screen.ConfirmScreen",             "net.minecraft.class_410"),
            Map.entry("net.minecraft.client.gui.screen.DisconnectedScreen",        "net.minecraft.class_411"),
            Map.entry("net.minecraft.client.gui.screen.GameMenuScreen",            "net.minecraft.class_418"),
            Map.entry("net.minecraft.client.gui.screen.OpenToLanScreen",           "net.minecraft.class_425"),
            Map.entry("net.minecraft.client.gui.screen.TitleScreen",               "net.minecraft.class_442"),
            Map.entry("net.minecraft.client.gui.screen.option.OnlineOptionsScreen","net.minecraft.class_6789"),
            Map.entry("net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen", "net.minecraft.class_5522"),
            // misc
            Map.entry("net.minecraft.server.integrated.IntegratedServer",          "net.minecraft.class_1132"),
            Map.entry("net.minecraft.server.MinecraftServer",                      "net.minecraft.server.MinecraftServer"),
            Map.entry("net.minecraft.client.option.GameOptions",                   "net.minecraft.class_315"),
            Map.entry("net.minecraft.client.option.KeyBinding",                    "net.minecraft.class_304")
    );

    // ---------------------------------------------------------------------
    // Method mappings: "YarnClassName.yarnMethodName" → intermediary method name
    // ---------------------------------------------------------------------
    private static final Map<String, String> METHOD_MAP = Map.ofEntries(
            // MinecraftClient (class_310)
            Map.entry("net.minecraft.client.MinecraftClient.getInstance",              "method_1551"),
            Map.entry("net.minecraft.client.MinecraftClient.setScreen",                 "method_1507"),
            Map.entry("net.minecraft.client.MinecraftClient.getServer",                 "method_1576"),
            Map.entry("net.minecraft.client.MinecraftClient.execute",                   "method_1508"),

            // ClientConnection (class_2535)
            Map.entry("net.minecraft.network.ClientConnection.addHandlers",               "method_48311"),
            Map.entry("net.minecraft.network.ClientConnection.setPacketListener",         "method_10763"),
            Map.entry("net.minecraft.network.ClientConnection.send",                      "method_10743"),
            Map.entry("net.minecraft.network.ClientConnection.setupEncryption",           "method_10756"),

            // ServerNetworkIo (class_3242)
            Map.entry("net.minecraft.server.ServerNetworkIo.getConnections",            "method_37909"),

            // Session (class_320)
            Map.entry("net.minecraft.client.util.Session.getAccessToken",               "method_1674"),

            // LoginHelloC2SPacket (class_2915) — record accessor
            Map.entry("net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket.name", "comp_765")
    );

    // ---------------------------------------------------------------------
    // Field mappings: "YarnClassName.yarnFieldName" → intermediary field name
    // ---------------------------------------------------------------------
    private static final Map<String, String> FIELD_MAP = Map.ofEntries(
            // MinecraftClient (class_310)
            Map.entry("net.minecraft.client.MinecraftClient.world",                     "field_1687"),
            Map.entry("net.minecraft.client.MinecraftClient.integratedServerConnection","field_1746"),
            Map.entry("net.minecraft.client.MinecraftClient.session",                   "field_1726"),
            Map.entry("net.minecraft.client.MinecraftClient.currentScreen",             "field_1707"),

            // ClientConnection (class_2535)
            Map.entry("net.minecraft.network.ClientConnection.channel",                 "field_11651"),
            Map.entry("net.minecraft.network.ClientConnection.address",                 "field_11645"),

            // MinecraftServer
            Map.entry("net.minecraft.server.MinecraftServer.networkIo",                 "field_4563")
    );

    private FabricReflect() {}

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Load a class by its Yarn name, automatically resolving to the
     * correct runtime name.
     */
    public static Class<?> mcClass(String yarnName) throws ClassNotFoundException {
        String runtimeName = CLASS_MAP.getOrDefault(yarnName, yarnName);
        try {
            return Class.forName(runtimeName);
        } catch (ClassNotFoundException e) {
            // Fallback: try the original Yarn name (works in dev environment)
            if (!runtimeName.equals(yarnName)) {
                try {
                    return Class.forName(yarnName);
                } catch (ClassNotFoundException ignored) {}
            }
            throw e;
        }
    }

    /**
     * Get a method by its Yarn name, automatically resolving to the
     * correct runtime method name.
     */
    public static Method mcMethod(Class<?> clazz, String yarnMethod, Class<?>... paramTypes)
            throws NoSuchMethodException {
        // First, try using the Yarn name directly (dev environment)
        try {
            return clazz.getMethod(yarnMethod, paramTypes);
        } catch (NoSuchMethodException ignored) {}

        // Try intermediary method name
        String key = findClassKey(clazz) + "." + yarnMethod;
        String intermediary = METHOD_MAP.get(key);
        if (intermediary != null) {
            try {
                return clazz.getMethod(intermediary, paramTypes);
            } catch (NoSuchMethodException ignored) {}
        }

        throw new NoSuchMethodException("Cannot find method " + yarnMethod +
                " in " + clazz.getName() + " (tried intermediary: " + intermediary + ")");
    }

    /**
     * Get a declared field by its Yarn name, automatically resolving to the
     * correct runtime field name.
     */
    public static Field mcField(Class<?> clazz, String yarnField)
            throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(yarnField);
        } catch (NoSuchFieldException ignored) {}

        String key = findClassKey(clazz) + "." + yarnField;
        String intermediary = FIELD_MAP.get(key);
        if (intermediary != null) {
            try {
                return clazz.getDeclaredField(intermediary);
            } catch (NoSuchFieldException ignored) {}
        }

        throw new NoSuchFieldException("Cannot find field " + yarnField +
                " in " + clazz.getName() + " (tried intermediary: " + intermediary + ")");
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    /**
     * Find the Yarn class key for a loaded class by reverse-mapping
     * its runtime name.
     */
    private static String findClassKey(Class<?> clazz) {
        String name = clazz.getName();
        // Direct reverse lookup
        for (Map.Entry<String, String> e : CLASS_MAP.entrySet()) {
            if (e.getValue().equals(name)) return e.getKey();
        }
        // If not in map, the name is the same in both namespaces
        return name;
    }
}
