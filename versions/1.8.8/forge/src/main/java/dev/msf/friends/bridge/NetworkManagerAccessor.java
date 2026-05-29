package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Reflection-based accessor for NetworkManager private fields in MC 1.8.8.
 * Replaces Mixin accessors for simpler compatibility with Forge 1.8.8.
 *
 * <p>MCP 1.8.8 field names:
 * <ul>
 *   <li>channel (NetworkManager) — io.netty.channel.Channel</li>
 *   <li>address (NetworkManager) — java.net.SocketAddress</li>
 * </ul>
 */
public final class NetworkManagerAccessor {
    private static final Logger LOGGER = Logging.get();
    private static final Map<Class<?>, Field> CHANNEL_FIELDS = new HashMap<>();
    private static final Map<Class<?>, Field> ADDRESS_FIELDS = new HashMap<>();

    static {
        try {
            Class<?> nmClass = Class.forName("net.minecraft.network.NetworkManager");
            // Try common MCP names
            String[] channelNames = {"channel", "field_150746_k", "field_179294_l"};
            String[] addressNames = {"address", "field_74453_a", "field_150739_c"};

            for (String name : channelNames) {
                try {
                    Field f = nmClass.getDeclaredField(name);
                    f.setAccessible(true);
                    CHANNEL_FIELDS.put(nmClass, f);
                    LOGGER.debug("[accessor] Found channel field: {}", name);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }

            for (String name : addressNames) {
                try {
                    Field f = nmClass.getDeclaredField(name);
                    f.setAccessible(true);
                    ADDRESS_FIELDS.put(nmClass, f);
                    LOGGER.debug("[accessor] Found address field: {}", name);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.warn("[accessor] Failed to find NetworkManager fields", e);
        }
    }

    public static void setChannel(Object networkManager, Channel channel) {
        Field f = CHANNEL_FIELDS.get(networkManager.getClass());
        if (f == null) {
            LOGGER.error("[accessor] No channel field found on {}", networkManager.getClass());
            return;
        }
        try { f.set(networkManager, channel); }
        catch (IllegalAccessException e) { LOGGER.error("[accessor] Cannot set channel", e); }
    }

    public static Channel getChannel(Object networkManager) {
        Field f = CHANNEL_FIELDS.get(networkManager.getClass());
        if (f == null) return null;
        try { return (Channel) f.get(networkManager); }
        catch (IllegalAccessException e) { return null; }
    }

    public static void setAddress(Object networkManager, SocketAddress address) {
        Field f = ADDRESS_FIELDS.get(networkManager.getClass());
        if (f == null) return;
        try { f.set(networkManager, address); }
        catch (IllegalAccessException e) { LOGGER.error("[accessor] Cannot set address", e); }
    }

    public static SocketAddress getAddress(Object networkManager) {
        Field f = ADDRESS_FIELDS.get(networkManager.getClass());
        if (f == null) return null;
        try { return (SocketAddress) f.get(networkManager); }
        catch (IllegalAccessException e) { return null; }
    }

    private NetworkManagerAccessor() {}
}
