package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Centralized reflection helper for MC 1.17.1 / Fabric.
 * Maps Mojang-mapped names to intermediary (Fabric production) names.
 *
 * <p>Intermediary names are generally stable across MC versions; this file
 * is identical to the 1.18.2 version.
 */
public final class FabricReflect {
    private static final Logger LOGGER = Logging.get();

    private static final Map<String, String> CLASS_MAP = Map.ofEntries(
            Map.entry("net.minecraft.client.Minecraft",                               "net.minecraft.class_310"),
            Map.entry("net.minecraft.client.User",                                    "net.minecraft.class_320"),
            Map.entry("net.minecraft.client.Session",                                 "net.minecraft.class_320"),
            Map.entry("net.minecraft.client.gui.screens.Screen",                      "net.minecraft.class_437"),
            Map.entry("net.minecraft.client.gui.components.DebugScreenOverlay",        "net.minecraft.class_340"),
            Map.entry("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl", "net.minecraft.class_635"),
            Map.entry("net.minecraft.client.multiplayer.ServerData",                   "net.minecraft.class_642"),
            Map.entry("net.minecraft.network.Connection",                             "net.minecraft.class_2535"),
            Map.entry("net.minecraft.network.protocol.PacketFlow",                    "net.minecraft.class_2598"),
            Map.entry("net.minecraft.network.PacketListener",                         "net.minecraft.class_2547"),
            Map.entry("net.minecraft.network.protocol.Packet",                        "net.minecraft.class_2596"),
            Map.entry("net.minecraft.network.protocol.login.ServerboundHelloPacket",  "net.minecraft.class_2915"),
            Map.entry("net.minecraft.network.protocol.login.ClientLoginPacketListener", "net.minecraft.class_2896"),
            Map.entry("net.minecraft.server.network.ServerConnectionListener",        "net.minecraft.class_3242"),
            Map.entry("net.minecraft.server.network.ServerHandshakePacketListenerImpl", "net.minecraft.class_3246")
    );

    private static final Map<String, String> METHOD_MAP = Map.ofEntries(
            Map.entry("net.minecraft.client.Minecraft.getInstance",                      "method_1551"),
            Map.entry("net.minecraft.client.Minecraft.getUser",                          "method_1548"),
            Map.entry("net.minecraft.client.Minecraft.getSession",                       "method_1548"),
            Map.entry("net.minecraft.client.Minecraft.setScreen",                        "method_1507"),
            Map.entry("net.minecraft.client.Minecraft.getSingleplayerServer",            "method_1576"),
            Map.entry("net.minecraft.network.Connection.send",                            "method_10743"),
            Map.entry("net.minecraft.server.network.ServerConnectionListener.getConnections", "method_37909"),
            Map.entry("net.minecraft.client.User.getAccessToken",                        "method_1674"),
            Map.entry("net.minecraft.client.User.getName",                               "method_1676"),
            Map.entry("net.minecraft.client.User.getUsername",                           "method_1676"),
            Map.entry("net.minecraft.client.User.getUuid",                               "method_1673"),
            Map.entry("net.minecraft.client.Session.getAccessToken",                     "method_1674"),
            Map.entry("net.minecraft.client.Session.getName",                              "method_1676"),
            Map.entry("net.minecraft.client.Session.getUsername",                        "method_1676"),
            Map.entry("net.minecraft.client.Session.getUuid",                            "method_1673")
    );

    private static final Map<String, String> FIELD_MAP = Map.ofEntries(
            Map.entry("net.minecraft.client.Minecraft.level",                            "field_1687"),
            Map.entry("net.minecraft.client.Minecraft.screen",                           "field_1755"),
            Map.entry("net.minecraft.client.Minecraft.currentScreen",                    "field_1755"),
            Map.entry("net.minecraft.client.Minecraft.session",                          "field_1726"),
            Map.entry("net.minecraft.network.Connection.channel",                        "field_11651"),
            Map.entry("net.minecraft.network.Connection.address",                        "field_11645"),
            Map.entry("net.minecraft.server.MinecraftServer.connection",                 "field_4563"),
            Map.entry("net.minecraft.client.User.accessToken",                           "field_1983"),
            Map.entry("net.minecraft.client.User.username",                              "field_1982"),
            Map.entry("net.minecraft.client.User.uuid",                                  "field_1985"),
            Map.entry("net.minecraft.client.Session.accessToken",                        "field_1983"),
            Map.entry("net.minecraft.client.Session.username",                           "field_1982"),
            Map.entry("net.minecraft.client.Session.uuid",                               "field_1985")
    );

    private FabricReflect() {}

    public static Class<?> mcClass(String mojangName) throws ClassNotFoundException {
        String runtimeName = CLASS_MAP.getOrDefault(mojangName, mojangName);
        try {
            return Class.forName(runtimeName);
        } catch (ClassNotFoundException e) {
            if (!runtimeName.equals(mojangName)) {
                try { return Class.forName(mojangName); } catch (ClassNotFoundException ignored) {}
            }
            throw e;
        }
    }

    public static Method mcMethod(Class<?> clazz, String mojangMethod, Class<?>... paramTypes)
            throws NoSuchMethodException {
        try { return clazz.getMethod(mojangMethod, paramTypes); } catch (NoSuchMethodException ignored) {}
        for (String classKey : findAllClassKeys(clazz)) {
            String key = classKey + "." + mojangMethod;
            String intermediary = METHOD_MAP.get(key);
            if (intermediary != null) {
                try { return clazz.getMethod(intermediary, paramTypes); } catch (NoSuchMethodException ignored) {}
            }
        }
        throw new NoSuchMethodException("Cannot find method " + mojangMethod +
                " in " + clazz.getName() + " (tried keys: " + findAllClassKeys(clazz) + ")");
    }

    public static Field mcField(Class<?> clazz, String mojangField) throws NoSuchFieldException {
        try { return clazz.getDeclaredField(mojangField); } catch (NoSuchFieldException ignored) {}
        for (String classKey : findAllClassKeys(clazz)) {
            String key = classKey + "." + mojangField;
            String intermediary = FIELD_MAP.get(key);
            if (intermediary != null) {
                try { return clazz.getDeclaredField(intermediary); } catch (NoSuchFieldException ignored) {}
            }
        }
        throw new NoSuchFieldException("Cannot find field " + mojangField +
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
