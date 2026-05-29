package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized reflection helper for MC 1.15.2 / Forge.
 * Maps Mojang-mapped names to their runtime SRG equivalents.
 * All APIs are Java 8 compatible.
 */
public final class FabricReflect {
    private static final Logger LOGGER = Logging.get();

    private static final Map<String, String> CLASS_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("net.minecraft.client.Minecraft", "net.minecraft.client.Minecraft");
        m.put("net.minecraft.client.gui.screen.TitleScreen", "net.minecraft.client.gui.screen.MainMenuScreen");
        m.put("net.minecraft.client.gui.screen.GameMenuScreen", "net.minecraft.client.gui.screen.IngameMenuScreen");
        m.put("net.minecraft.client.gui.screen.OpenToLanScreen", "net.minecraft.client.gui.screen.ShareToLanScreen");
        m.put("net.minecraft.client.options.GameOptions", "net.minecraft.client.GameSettings");
        m.put("net.minecraft.client.network.ClientPlayNetworkHandler", "net.minecraft.client.network.play.ClientPlayNetHandler");
        m.put("net.minecraft.network.ClientConnection", "net.minecraft.network.NetworkManager");
        m.put("net.minecraft.server.network.ServerLoginNetworkHandler", "net.minecraft.network.login.ServerLoginNetHandler");
        CLASS_MAP = m;
    }

    private static final Map<String, String> METHOD_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("net.minecraft.client.Minecraft.getInstance", "getInstance");
        m.put("net.minecraft.client.Minecraft.getUser", "getUser");
        m.put("net.minecraft.client.Minecraft.getSession", "getSession");
        m.put("net.minecraft.client.Minecraft.setScreen", "setScreen");
        m.put("net.minecraft.client.Minecraft.getSingleplayerServer", "getSingleplayerServer");
        m.put("net.minecraft.network.ClientConnection.send", "send");
        m.put("net.minecraft.server.network.ServerConnectionListener.getConnections", "getConnections");
        m.put("net.minecraft.client.User.getAccessToken", "getAccessToken");
        m.put("net.minecraft.client.User.getName", "getName");
        m.put("net.minecraft.client.User.getUsername", "getUsername");
        m.put("net.minecraft.client.User.getUuid", "getUuid");
        METHOD_MAP = m;
    }

    private static final Map<String, String> FIELD_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("net.minecraft.client.Minecraft.level", "world");
        m.put("net.minecraft.client.Minecraft.screen", "currentScreen");
        m.put("net.minecraft.client.Minecraft.currentScreen", "currentScreen");
        m.put("net.minecraft.client.Minecraft.session", "session");
        m.put("net.minecraft.network.ClientConnection.channel", "channel");
        m.put("net.minecraft.network.ClientConnection.address", "address");
        m.put("net.minecraft.server.MinecraftServer.connection", "connection");
        m.put("net.minecraft.client.User.accessToken", "accessToken");
        m.put("net.minecraft.client.User.username", "username");
        m.put("net.minecraft.client.User.uuid", "uuid");
        FIELD_MAP = m;
    }

    public static Class<?> mcClass(String mojangName) throws ClassNotFoundException {
        String srg = CLASS_MAP.getOrDefault(mojangName, mojangName);
        return Class.forName(srg);
    }

    public static Method mcMethod(Class<?> clazz, String mojangMethodName) throws NoSuchMethodException {
        String key = clazz.getName() + "." + mojangMethodName;
        String methodName = METHOD_MAP.getOrDefault(key, mojangMethodName);
        Class<?> searchClass = clazz;
        while (searchClass != null) {
            try { return searchClass.getDeclaredMethod(methodName); }
            catch (NoSuchMethodException ignored) {}
            for (Class<?> iface : searchClass.getInterfaces()) {
                try { return iface.getDeclaredMethod(methodName); }
                catch (NoSuchMethodException ignored) {}
            }
            searchClass = searchClass.getSuperclass();
        }
        throw new NoSuchMethodException("Method " + methodName + " not found on " + clazz.getName());
    }

    public static Field mcField(Class<?> clazz, String mojangFieldName) throws NoSuchFieldException {
        String key = clazz.getName() + "." + mojangFieldName;
        String fieldName = FIELD_MAP.getOrDefault(key, mojangFieldName);
        Class<?> searchClass = clazz;
        while (searchClass != null) {
            try { return searchClass.getDeclaredField(fieldName); }
            catch (NoSuchFieldException ignored) {}
            searchClass = searchClass.getSuperclass();
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found on " + clazz.getName());
    }

    private FabricReflect() {}
}
