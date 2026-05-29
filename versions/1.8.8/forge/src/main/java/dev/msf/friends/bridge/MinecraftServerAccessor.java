package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Reflection-based accessor for MinecraftServer private fields in MC 1.8.8.
 */
public final class MinecraftServerAccessor {
    private static final Logger LOGGER = Logging.get();
    private static final Map<Class<?>, Field> NETWORK_SYSTEM_FIELDS = new HashMap<>();

    static {
        try {
            Class<?> serverClass = MinecraftServer.class;
            String[] names = {"networkSystem", "field_147144_o"};
            for (String name : names) {
                try {
                    Field f = serverClass.getDeclaredField(name);
                    f.setAccessible(true);
                    NETWORK_SYSTEM_FIELDS.put(serverClass, f);
                    LOGGER.debug("[accessor] Found networkSystem field: {}", name);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.warn("[accessor] Failed to find MinecraftServer.networkSystem field", e);
        }
    }

    /**
     * Get the NetworkSystem from the MinecraftServer.
     * Returns null if field not found.
     */
    public static Object getNetworkSystem(MinecraftServer server) {
        Field f = NETWORK_SYSTEM_FIELDS.get(server.getClass());
        if (f == null) return null;
        try { return f.get(server); }
        catch (IllegalAccessException e) { return null; }
    }

    /**
     * Get the list of NetworkManager objects from the NetworkSystem.
     */
    @SuppressWarnings("unchecked")
    public static java.util.List<?> getNetworkManagers(Object networkSystem) {
        if (networkSystem == null) return java.util.Collections.emptyList();
        try {
            Field f = networkSystem.getClass().getDeclaredField("networkManagers");
            f.setAccessible(true);
            return (java.util.List<?>) f.get(networkSystem);
        } catch (Exception e) {
            LOGGER.debug("[accessor] Could not get networkManagers list", e);
            return java.util.Collections.emptyList();
        }
    }

    private MinecraftServerAccessor() {}
}
