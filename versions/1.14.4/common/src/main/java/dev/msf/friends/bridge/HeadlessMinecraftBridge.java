package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Reflection-driven {@link MinecraftBridge} implementation for MC 1.14.4.
 *
 * <p>Pulls credentials from {@code MinecraftClient.getInstance().getSession()} and
 * everything else from a small set of getters. When MC isn't reachable
 * (eg. running outside the game for tests) all status getters return safe
 * defaults; {@code joinHost} / {@code acceptGuest} throw to make missing
 * wiring obvious to callers.
 *
 * <p>1.14.4 differences from 1.16.5:
 * <ul>
 *   <li>Session.getUuid() returns a String, not getUuidOrNull() returning UUID</li>
 *   <li>Session has getProfile() returning a GameProfile</li>
 *   <li>No disconnectWithProgressScreen — use disconnect()</li>
 *   <li>No SocialInteractionsScreen</li>
 * </ul>
 */
public final class HeadlessMinecraftBridge implements MinecraftBridge {
    private static final Logger LOGGER = Logging.get();

    public record CredentialOverride(UUID profileId, String accessToken, String userName) {}

    /** Optional override populated via system properties for offline development. */
    public static volatile @Nullable CredentialOverride override;

    private final Executor mainThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "msf-mc-bridge"); t.setDaemon(true); return t;
    });

    private volatile MinecraftBridge.PresenceSharing presenceSharingMode = MinecraftBridge.PresenceSharing.ALL;
    private volatile boolean hiddenMode;
    private volatile MinecraftBridge.MultiplayerScope multiplayerScopeMode = MinecraftBridge.MultiplayerScope.OFF;
    private volatile boolean connectedViaP2P = false;

    @Override public boolean isConnectedViaP2P() { return connectedViaP2P; }
    @Override public void setConnectedViaP2P(boolean connected) { this.connectedViaP2P = connected; }
    @Override public void setPresenceSharingMode(MinecraftBridge.PresenceSharing mode) { this.presenceSharingMode = mode; }
    @Override public MinecraftBridge.PresenceSharing presenceSharing() { return presenceSharingMode; }
    @Override public boolean hiddenMode() { return hiddenMode; }

    @Override public void setHiddenMode(boolean hidden) {
        LOGGER.info("[bridge] hidden mode changed: {} -> {}", hiddenMode, hidden);
        this.hiddenMode = hidden;
    }

    @Override public MinecraftBridge.MultiplayerScope multiplayerScope() { return multiplayerScopeMode; }

    @Override public void setMultiplayerScope(MinecraftBridge.MultiplayerScope scope) {
        LOGGER.info("[bridge] multiplayer scope changed: {} -> {}", multiplayerScopeMode, scope);
        this.multiplayerScopeMode = scope;
    }

    @Override public UUID profileId() { return tryFetchOrThrow().profileId; }
    @Override public String userName() { return tryFetchOrThrow().userName; }
    @Override public String accessToken() { return tryFetchOrThrow().accessToken; }

    @Override public boolean isHostingP2P() {
        return multiplayerScopeMode == MinecraftBridge.MultiplayerScope.ONLINE;
    }

    @Override public boolean inLevel() {
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
            Object instance;
            try {
                instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            } catch (NoSuchMethodException e) {
                instance = mc.getMethod("method_1551").invoke(null);
            }
            if (instance == null) return false;
            Field f;
            try { f = mc.getDeclaredField("field_1687"); }
            catch (NoSuchFieldException e) { f = FabricReflect.mcField(mc, "level"); }
            f.setAccessible(true);
            return f.get(instance) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public void executeOnClientThread(Runnable r) {
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance != null) {
                Method execute = mc.getMethod("execute", Runnable.class);
                execute.invoke(instance, r);
                return;
            }
        } catch (Exception ignored) {}
        mainThread.execute(r);
    }

    @Override public void notifyToast(String type, String name, @Nullable UUID profileId) {
        try {
            dev.msf.friends.bridge.ToastBridge.showToast(type, name, profileId);
        } catch (Throwable t) {
            LOGGER.debug("[bridge] toast failed: {}", t.getMessage());
        }
    }

    /**
     * Try to fetch credentials from the running Minecraft instance.
     */
    public static Optional<Snapshot> tryFetch() {
        if (override != null) {
            return Optional.of(new Snapshot(override.profileId, override.accessToken, override.userName));
        }
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) return Optional.empty();

            Object session = FabricReflect.mcMethod(mc, "getSession").invoke(instance);
            if (session == null) return Optional.empty();

            Class<?> sessionClass = session.getClass();
            String token = (String) FabricReflect.mcMethod(sessionClass, "getAccessToken").invoke(session);
            String name = (String) FabricReflect.mcMethod(sessionClass, "getUsername").invoke(session);
            String uuidStr = (String) FabricReflect.mcMethod(sessionClass, "getUuid").invoke(session);

            if (token == null || token.isEmpty() || uuidStr == null || uuidStr.isEmpty()) {
                return Optional.empty();
            }
            UUID uuid = UUID.fromString(uuidStr);
            return Optional.of(new Snapshot(uuid, token, name));
        } catch (Exception e) {
            LOGGER.debug("[bridge] Could not fetch credentials: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Snapshot tryFetchOrThrow() {
        return tryFetch().orElseThrow(() ->
                new IllegalStateException("Minecraft credentials not available"));
    }

    public record Snapshot(UUID profileId, String accessToken, String userName) {}
}
