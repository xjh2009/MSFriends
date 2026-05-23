package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Centralized reflection helper that resolves Mojang-mapped names to their
 * runtime equivalents for MC 1.19.2 / Fabric. On Fabric with yarn mappings,
 * the dev environment uses yarn names but the production runtime uses
 * intermediary names (e.g. {@code class_310} instead of {@code Minecraft}).
 *
 * <p>All reflective access to Minecraft classes, methods, and fields must go
 * through this class so that the correct names are used at runtime.
 */
public final class FabricReflect {
    private static final Logger LOGGER = Logging.get();

    // ---------------------------------------------------------------------
    // Class name mappings: Mojang → Intermediary (1.19.2)
    // ---------------------------------------------------------------------
    private static final Map<String, String> CLASS_MAP = Map.ofEntries(
            // client
            Map.entry("net.minecraft.client.Minecraft",                               "net.minecraft.class_310"),
            Map.entry("net.minecraft.client.User",                                    "net.minecraft.class_320"),  // 1.19.2 Yarn: Session
            Map.entry("net.minecraft.client.Session",                                 "net.minecraft.class_320"),  // alias
            Map.entry("net.minecraft.client.gui.screens.Screen",                      "net.minecraft.class_437"),
            Map.entry("net.minecraft.client.gui.components.DebugScreenOverlay",        "net.minecraft.class_340"),
            Map.entry("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl", "net.minecraft.class_635"),
            Map.entry("net.minecraft.client.multiplayer.ServerData",                   "net.minecraft.class_642"),
            // network
            Map.entry("net.minecraft.network.Connection",                             "net.minecraft.class_2535"),
            Map.entry("net.minecraft.network.protocol.PacketFlow",                    "net.minecraft.class_2598"),
            Map.entry("net.minecraft.network.PacketListener",                         "net.minecraft.class_2547"),
            Map.entry("net.minecraft.network.protocol.Packet",                        "net.minecraft.class_2596"),
            Map.entry("net.minecraft.network.protocol.login.ServerboundHelloPacket",  "net.minecraft.class_2915"),
            Map.entry("net.minecraft.network.protocol.login.ClientLoginPacketListener", "net.minecraft.class_2896"),
            // server
            Map.entry("net.minecraft.server.network.ServerConnectionListener",        "net.minecraft.class_3242"),
            Map.entry("net.minecraft.server.network.ServerHandshakePacketListenerImpl", "net.minecraft.class_3246")
    );

    // ---------------------------------------------------------------------
    // Method mappings: "ClassName.methodName" → intermediary method name (1.19.2)
    // ---------------------------------------------------------------------
    private static final Map<String, String> METHOD_MAP = Map.ofEntries(
            // Minecraft (class_310)
            Map.entry("net.minecraft.client.Minecraft.getInstance",                      "method_1551"),
            Map.entry("net.minecraft.client.Minecraft.getUser",                          "method_1548"),  // 1.19.2 Yarn: getSession
            Map.entry("net.minecraft.client.Minecraft.getSession",                       "method_1548"),  // alias
            Map.entry("net.minecraft.client.Minecraft.setScreen",                        "method_1507"),
            Map.entry("net.minecraft.client.Minecraft.getSingleplayerServer",            "method_1576"),

            // Connection (class_2535)
            Map.entry("net.minecraft.network.Connection.send",                            "method_10743"),

            // ServerConnectionListener (class_3242)
            Map.entry("net.minecraft.server.network.ServerConnectionListener.getConnections", "method_37909"),

            // User / Session (class_320) — 1.19.2 Yarn: Session
            Map.entry("net.minecraft.client.User.getAccessToken",                        "method_1674"),
            Map.entry("net.minecraft.client.User.getName",                               "method_1676"),  // 1.19.2 Yarn: getUsername
            Map.entry("net.minecraft.client.User.getUsername",                           "method_1676"),  // alias
            Map.entry("net.minecraft.client.User.getUuid",                               "method_1673"),  // returns String
            Map.entry("net.minecraft.client.User.getUuidOrNull",                         "method_44717"), // returns UUID
            Map.entry("net.minecraft.client.Session.getAccessToken",                     "method_1674"),
            Map.entry("net.minecraft.client.Session.getName",                              "method_1676"),  // alias for getUsername
            Map.entry("net.minecraft.client.Session.getUsername",                        "method_1676"),
            Map.entry("net.minecraft.client.Session.getUuid",                            "method_1673"),
            Map.entry("net.minecraft.client.Session.getUuidOrNull",                      "method_44717")
    );

    // ---------------------------------------------------------------------
    // Field mappings: "ClassName.fieldName" → intermediary field name (1.19.2)
    // ---------------------------------------------------------------------
    private static final Map<String, String> FIELD_MAP = Map.ofEntries(
            // Minecraft (class_310)
            Map.entry("net.minecraft.client.Minecraft.level",                            "field_1687"),
            Map.entry("net.minecraft.client.Minecraft.screen",                           "field_1755"),  // 1.19.2 Yarn: currentScreen
            Map.entry("net.minecraft.client.Minecraft.currentScreen",                    "field_1755"),  // alias
            Map.entry("net.minecraft.client.Minecraft.session",                          "field_1726"),  // 1.19.2 Yarn: session

            // Connection (class_2535)
            Map.entry("net.minecraft.network.Connection.channel",                        "field_11651"),
            Map.entry("net.minecraft.network.Connection.address",                        "field_11645"),

            // MinecraftServer
            Map.entry("net.minecraft.server.MinecraftServer.connection",                 "field_4563"),

            // User / Session (class_320) — 1.19.2 Yarn: Session
            Map.entry("net.minecraft.client.User.accessToken",                           "field_1983"),
            Map.entry("net.minecraft.client.User.username",                              "field_1982"),
            Map.entry("net.minecraft.client.User.uuid",                                  "field_1985"),
            Map.entry("net.minecraft.client.Session.accessToken",                        "field_1983"),
            Map.entry("net.minecraft.client.Session.username",                           "field_1982"),
            Map.entry("net.minecraft.client.Session.uuid",                               "field_1985")
    );

    private FabricReflect() {}

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Load a class by its Mojang name, automatically resolving to the
     * correct runtime name.
     */
    public static Class<?> mcClass(String mojangName) throws ClassNotFoundException {
        String runtimeName = CLASS_MAP.getOrDefault(mojangName, mojangName);
        try {
            return Class.forName(runtimeName);
        } catch (ClassNotFoundException e) {
            // Fallback: try the original Mojang name (works in dev environment)
            if (!runtimeName.equals(mojangName)) {
                try {
                    return Class.forName(mojangName);
                } catch (ClassNotFoundException ignored) {}
            }
            throw e;
        }
    }

    /**
     * Get a method by its Mojang name, automatically resolving to the
     * correct runtime method name. Tries ALL possible class key mappings
     * since the same intermediary class may map to multiple Mojang names.
     */
    public static Method mcMethod(Class<?> clazz, String mojangMethod, Class<?>... paramTypes)
            throws NoSuchMethodException {
        // First, try using the Mojang name directly (dev environment)
        try {
            return clazz.getMethod(mojangMethod, paramTypes);
        } catch (NoSuchMethodException ignored) {}

        // Try all possible class keys — order is not guaranteed for Map.ofEntries
        for (String classKey : findAllClassKeys(clazz)) {
            String key = classKey + "." + mojangMethod;
            String intermediary = METHOD_MAP.get(key);
            if (intermediary != null) {
                try {
                    return clazz.getMethod(intermediary, paramTypes);
                } catch (NoSuchMethodException ignored) {}
            }
        }

        throw new NoSuchMethodException("Cannot find method " + mojangMethod +
                " in " + clazz.getName() + " (tried keys: " + findAllClassKeys(clazz) + ")");
    }

    /**
     * Get a declared field by its Mojang name, automatically resolving to the
     * correct runtime field name. Tries ALL possible class key mappings.
     */
    public static Field mcField(Class<?> clazz, String mojangField)
            throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(mojangField);
        } catch (NoSuchFieldException ignored) {}

        for (String classKey : findAllClassKeys(clazz)) {
            String key = classKey + "." + mojangField;
            String intermediary = FIELD_MAP.get(key);
            if (intermediary != null) {
                try {
                    return clazz.getDeclaredField(intermediary);
                } catch (NoSuchFieldException ignored) {}
            }
        }

        throw new NoSuchFieldException("Cannot find field " + mojangField +
                " in " + clazz.getName() + " (tried keys: " + findAllClassKeys(clazz) + ")");
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    /**
     * Find the Mojang class key for a loaded class by reverse-mapping
     * its runtime name. Returns ALL matching Mojang keys (since the same
     * intermediary class may map to multiple Mojang names, e.g. User/Session).
     */
    private static List<String> findAllClassKeys(Class<?> clazz) {
        String name = clazz.getName();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, String> e : CLASS_MAP.entrySet()) {
            if (e.getValue().equals(name)) keys.add(e.getKey());
        }
        if (keys.isEmpty()) keys.add(name); // Not in map — name is same in both
        return keys;
    }

    /**
     * Find a single Mojang class key (returns the first match for compat).
     */
    private static String findClassKey(Class<?> clazz) {
        List<String> all = findAllClassKeys(clazz);
        return all.get(0);
    }
}
