package dev.msf.friends.bridge;

import org.apache.logging.log4j.Logger;
import dev.msf.friends.MsfFriendsConstants;

import java.lang.reflect.Method;

/**
 * Reflection helper for Forge 1.8.9 classes.
 * Java 8 compatible.
 */
public final class ForgeReflect {

    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;

    private ForgeReflect() {}

    /**
     * Get a class by its fully-qualified obfuscated/deobfuscated name.
     */
    public static Class<?> mcClass(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    /**
     * Get a method from a class by name and parameter types.
     */
    public static Method mcMethod(Class<?> clazz, String name, Class<?>... params) throws NoSuchMethodException {
        Method m = clazz.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    /**
     * Get the Minecraft singleton via reflection.
     */
    public static Object getMinecraft() {
        try {
            Class<?> mcClass = mcClass("net.minecraft.client.Minecraft");
            Method getMc = mcClass.getMethod("getMinecraft");
            return getMc.invoke(null);
        } catch (Exception e) {
            LOGGER.debug("[bridge] getMinecraft() failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the integrated server (if running).
     */
    public static Object getIntegratedServer() {
        try {
            Object mc = getMinecraft();
            if (mc == null) return null;
            Method getServer = mc.getClass().getMethod("getIntegratedServer");
            return getServer.invoke(mc);
        } catch (Exception e) {
            LOGGER.debug("[bridge] getIntegratedServer() failed: {}", e.getMessage());
            return null;
        }
    }
}
