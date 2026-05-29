package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Centralized reflection helper for MC 1.14.4 / Fabric.
 * Maps Mojang-mapped names to their runtime intermediary equivalents.
 *
 * <p>1.14.4 intermediary mappings differ from 1.16.5; method/field IDs are
 * different.
 */
public final class FabricReflect {
    private static final Logger LOGGER = Logging.get();

    // ---------------------------------------------------------------------
    // Class name mappings: Mojang → Intermediary (1.14.4)
    // ---------------------------------------------------------------------
    private static final Map<String, String> CLASS_MAP = Map.ofEntries(
            // client
            Map.entry("net.minecraft.client.Minecraft",                               "net.minecraft.class_310"),
            Map.entry("net.minecraft.client.User",                                    "net.minecraft.class_320"),
            Map.entry("net.minecraft.client.Session",                                 "net.minecraft.class_320"),
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
    // Method mappings: "ClassName.methodName" → intermediary method name (1.14.4)
    // ---------------------------------------------------------------------
    private static final Map<String, String> METHOD_MAP = Map.ofEntries(
            // MinecraftClient (class_310)
            Map.entry("net.minecraft.client.Minecraft.getInstance",                      "method_1551"),
            Map.entry("net.minecraft.client.Minecraft.getUser",                          "method_1548"),
            Map.entry("net.minecraft.client.Minecraft.getSession",                       "method_1548"),
            Map.entry("net.minecraft.client.Minecraft.setScreen",                        "method_1507"),
            Map.entry("net.minecraft.client.Minecraft.getSingleplayerServer",            "method_1576"),

            // Connection (class_2535)
            Map.entry("net.minecraft.network.Connection.send",                            "method_10743"),

            // ServerConnectionListener (class_3242)
            Map.entry("net.minecraft.server.network.ServerConnectionListener.getConnections", "method_14650"),

            // Session (class_320)
            Map.entry("net.minecraft.client.User.getAccessToken",                        "method_1674"),
            Map.entry("net.minecraft.client.User.getName",                               "method_1676"),
            Map.entry("net.minecraft.client.User.getUsername",                           "method_1676"),
            Map.entry("net.minecraft.client.User.getUuid",                               "method_1673"),
            Map.entry("net.minecraft.client.Session.getAccessToken",                     "method_1674"),
            Map.entry("net.minecraft.client.Session.getName",                              "method_1676"),
            Map.entry("net.minecraft.client.Session.getUsername",                        "method_1676"),
            Map.entry("net.minecraft.client.Session.getUuid",                            "method_1673")
    );

    // ---------------------------------------------------------------------
    // Field mappings: "ClassName.fieldName" → intermediary field name (1.14.4)
    // ---------------------------------------------------------------------
    private static final Map<String, String> FIELD_MAP = Map.ofEntries(
            // MinecraftClient (class_310)
            Map.entry("net.minecraft.client.Minecraft.level",                            "field_1687"),
            Map.entry("net.minecraft.client.Minecraft.screen",                           "field_1755"),
            Map.entry("net.minecraft.client.Minecraft.currentScreen",                    "field_1755"),
            Map.entry("net.minecraft.client.Minecraft.session",                          "field_1726"),

            // Connection (class_2535)
            Map.entry("net.minecraft.network.Connection.channel",                        "field_11651"),
            Map.entry("net.minecraft.network.Connection.address",                        "field_11645"),

            // MinecraftServer
            Map.entry("net.minecraft.server.MinecraftServer.connection",                 "field_3769"),

            // Session (class_320)
            Map.entry("net.minecraft.client.User.accessToken",                           "field_1983"),
            Map.entry("net.minecraft.client.User.username",                              "field_1982"),
            Map.entry("net.minecraft.client.User.uuid",                                  "field_1985"),
            Map.entry("net.minecraft.client.Session.accessToken",                        "field_1983"),
            Map.entry("net.minecraft.client.Session.username",                           "field_1982"),
            Map.entry("net.minecraft.client.Session.uuid",                               "field_1985")
    );

    /**
     * Resolve a Minecraft class by its Mojang-mapped name.
     */
    public static Class<?> mcClass(String mojangName) throws ClassNotFoundException {
        String intermediary = CLASS_MAP.getOrDefault(mojangName, mojangName);
        return Class.forName(intermediary);
    }

    /**
     * Resolve a method on a class by its Mojang-mapped "ClassName.methodName" key.
     */
    public static Method mcMethod(Class<?> clazz, String mojangMethodName) throws NoSuchMethodException {
        String key = clazz.getName().replace('.', '/').replace('/', '.');
        // Try to find the key in METHOD_MAP by matching the simple class name
        String methodName = null;
        for (var entry : METHOD_MAP.entrySet()) {
            if (entry.getKey().endsWith("." + mojangMethodName) && key.contains(entry.getKey().substring(0, entry.getKey().lastIndexOf('.')))) {
                methodName = entry.getValue();
                break;
            }
        }
        if (methodName == null) methodName = mojangMethodName;

        // Search through the class hierarchy
        Class<?> searchClass = clazz;
        while (searchClass != null) {
            try {
                return searchClass.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {}
            for (Class<?> iface : searchClass.getInterfaces()) {
                try {
                    return iface.getDeclaredMethod(methodName);
                } catch (NoSuchMethodException ignored) {}
            }
            searchClass = searchClass.getSuperclass();
        }
        throw new NoSuchMethodException("Method " + methodName + " not found on " + clazz.getName());
    }

    /**
     * Resolve a field on a class by its Mojang-mapped "ClassName.fieldName" key.
     */
    public static Field mcField(Class<?> clazz, String mojangFieldName) throws NoSuchFieldException {
        String key = clazz.getName().replace('.', '/').replace('/', '.');
        String fieldName = null;
        for (var entry : FIELD_MAP.entrySet()) {
            if (entry.getKey().endsWith("." + mojangFieldName) && key.contains(entry.getKey().substring(0, entry.getKey().lastIndexOf('.')))) {
                fieldName = entry.getValue();
                break;
            }
        }
        if (fieldName == null) fieldName = mojangFieldName;

        Class<?> searchClass = clazz;
        while (searchClass != null) {
            try {
                return searchClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {}
            searchClass = searchClass.getSuperclass();
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found on " + clazz.getName());
    }

    private FabricReflect() {}
}
