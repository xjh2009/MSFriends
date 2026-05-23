package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Centralized reflection helper that resolves Mojang-mapped names to their
 * runtime equivalents for MC 1.18.2 / Fabric. On Fabric with yarn mappings,
 * the dev environment uses yarn names but the production runtime uses
 * intermediary names (e.g. {@code class_310} instead of {@code Minecraft}).
 *
 * <p>All reflective access to Minecraft classes, methods, and fields must go
 * through this class so that the correct names are used at runtime.
 *
 * <p>1.18.2 intermediary mapping values sourced from Fabric intermediary database.
 */
public final class FabricReflect {
    private static final Logger LOGGER = Logging.get();

    // ---------------------------------------------------------------------
    // Class name mappings: Mojang → Intermediary (1.18.2)
    // ---------------------------------------------------------------------
    private static final Map<String, String> CLASS_MAP = Map.ofEntries(
            // client
            Map.entry("net.minecraft.client.Minecraft",                               "net.minecraft.class_310"),
            Map.entry("net.minecraft.client.User",                                    "net.minecraft.class_320"),
            Map.entry("net.minecraft.client.Options",                                  "net.minecraft.class_315"),
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
            Map.entry("net.minecraft.server.network.ServerHandshakePacketListenerImpl", "net.minecraft.class_3246"),
            Map.entry("net.minecraft.server.network.ServerLoginPacketListenerImpl",    "net.minecraft.class_3248")
    );

    // ---------------------------------------------------------------------
    // Method mappings: "ClassName.methodName" → intermediary method name (1.18.2)
    // ---------------------------------------------------------------------
    private static final Map<String, String> METHOD_MAP = Map.ofEntries(
            // Minecraft (class_310)
            Map.entry("net.minecraft.client.Minecraft.getInstance",                      "method_1551"),
            Map.entry("net.minecraft.client.Minecraft.getUser",                          "method_1548"),
            Map.entry("net.minecraft.client.Minecraft.setScreen",                        "method_1507"),
            Map.entry("net.minecraft.client.Minecraft.getSingleplayerServer",            "method_1576"),
            Map.entry("net.minecraft.client.Minecraft.execute",                          "method_1552"),
            Map.entry("net.minecraft.client.Minecraft.disconnect",                       "method_1497"),
            Map.entry("net.minecraft.client.Minecraft.clearLevel",                       "method_1490"),
            Map.entry("net.minecraft.client.Minecraft.prepareForMultiplayer",            "method_1672"),

            // Connection (class_2535)
            Map.entry("net.minecraft.network.Connection.send",                            "method_10743"),
            Map.entry("net.minecraft.network.Connection.setEncryptionKey",                "method_10750"),
            Map.entry("net.minecraft.network.Connection.setPacketListener",                "method_10763"),

            // ClientPacketListener (class_646)
            Map.entry("net.minecraft.client.multiplayer.ClientPacketListener.getConnection", "method_2872"),

            // ServerConnectionListener (class_3242)
            Map.entry("net.minecraft.server.network.ServerConnectionListener.getConnections", "method_37909"),

            // User (class_320)
            Map.entry("net.minecraft.client.User.getAccessToken",                        "method_1674"),
            Map.entry("net.minecraft.client.User.getName",                               "method_1676"),
            Map.entry("net.minecraft.client.User.getUuid",                               "method_30566"),

            // ServerLoginPacketListenerImpl (class_3248)
            Map.entry("net.minecraft.server.network.ServerLoginPacketListenerImpl.handleAcceptedLogin", "method_14384")
    );

    // ---------------------------------------------------------------------
    // Field mappings: "ClassName.fieldName" → intermediary field name (1.18.2)
    // ---------------------------------------------------------------------
    private static final Map<String, String> FIELD_MAP = Map.ofEntries(
            // Minecraft (class_310)
            Map.entry("net.minecraft.client.Minecraft.level",                            "field_1687"),
            Map.entry("net.minecraft.client.Minecraft.screen",                           "field_1755"),
            Map.entry("net.minecraft.client.Minecraft.pendingConnection",                "field_1691"),

            // Connection (class_2535)
            Map.entry("net.minecraft.network.Connection.channel",                        "field_11651"),
            Map.entry("net.minecraft.network.Connection.address",                        "field_11645"),

            // Options (class_315)
            Map.entry("net.minecraft.client.Options.keyMappings",                        "field_1839"),

            // MinecraftServer
            Map.entry("net.minecraft.server.MinecraftServer.connection",                 "field_4563"),

            // ClientPacketListener (class_646)
            Map.entry("net.minecraft.client.multiplayer.ClientPacketListener.connection", "field_3689"),

            // ServerLoginPacketListenerImpl (class_3248)
            Map.entry("net.minecraft.server.network.ServerLoginPacketListenerImpl.server",       "field_14162"),
            Map.entry("net.minecraft.server.network.ServerLoginPacketListenerImpl.connection",  "field_14158"),
            Map.entry("net.minecraft.server.network.ServerLoginPacketListenerImpl.gameProfile", "field_14160")
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
     * correct runtime method name.
     */
    public static Method mcMethod(Class<?> clazz, String mojangMethod, Class<?>... paramTypes)
            throws NoSuchMethodException {
        // First, try using the Mojang name directly (dev environment)
        try {
            return clazz.getMethod(mojangMethod, paramTypes);
        } catch (NoSuchMethodException ignored) {}

        // Try intermediary method name
        String key = findClassKey(clazz) + "." + mojangMethod;
        String intermediary = METHOD_MAP.get(key);
        if (intermediary != null) {
            try {
                return clazz.getMethod(intermediary, paramTypes);
            } catch (NoSuchMethodException ignored) {}
        }

        throw new NoSuchMethodException("Cannot find method " + mojangMethod +
                " in " + clazz.getName() + " (tried intermediary: " + intermediary + ")");
    }

    /**
     * Get a declared field by its Mojang name, automatically resolving to the
     * correct runtime field name.
     */
    public static Field mcField(Class<?> clazz, String mojangField)
            throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(mojangField);
        } catch (NoSuchFieldException ignored) {}

        String key = findClassKey(clazz) + "." + mojangField;
        String intermediary = FIELD_MAP.get(key);
        if (intermediary != null) {
            try {
                return clazz.getDeclaredField(intermediary);
            } catch (NoSuchFieldException ignored) {}
        }

        throw new NoSuchFieldException("Cannot find field " + mojangField +
                " in " + clazz.getName() + " (tried intermediary: " + intermediary + ")");
    }

    /**
     * Get a declared method by its Mojang name (includes private methods),
     * automatically resolving to the correct runtime method name.
     */
    public static Method mcDeclaredMethod(Class<?> clazz, String mojangMethod, Class<?>... paramTypes)
            throws NoSuchMethodException {
        // First, try using the Mojang name directly (dev environment)
        try {
            Method m = clazz.getDeclaredMethod(mojangMethod, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {}

        // Try intermediary method name
        String key = findClassKey(clazz) + "." + mojangMethod;
        String intermediary = METHOD_MAP.get(key);
        if (intermediary != null) {
            try {
                Method m = clazz.getDeclaredMethod(intermediary, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }

        throw new NoSuchMethodException("Cannot find declared method " + mojangMethod +
                " in " + clazz.getName() + " (tried intermediary: " + intermediary + ")");
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    /**
     * Find the Mojang class key for a loaded class by reverse-mapping
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
