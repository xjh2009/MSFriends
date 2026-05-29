package dev.msf.friends.bridge;

import dev.msf.friends.Logging1112;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Reflection-driven MinecraftBridge for MC 1.11.2 (MCP mappings).
 *
 * Uses MCP-mapped names for MC 1.11.2:
 * - net.minecraft.client.Minecraft.getMinecraft()
 * - net.minecraft.client.Minecraft.getSession() returns Session
     * - Session.getUsername(), Session.getPlayerID(), Session.getToken()
     * - net.minecraft.client.Minecraft.world - WorldClient (null if not in world)
 */
public final class HeadlessMinecraftBridge1112 implements MinecraftBridge1112 {

    private static final Logger LOGGER = Logging1112.get();
    private static final Executor MAIN_THREAD_EXECUTOR = new MainThreadExecutor();
    private static final Executor MAIN_THREAD = MAIN_THREAD_EXECUTOR;

    private volatile boolean connectedViaP2P = false;
    private volatile PresenceSharing presenceSharingMode = PresenceSharing.LIMITED;
    private volatile boolean hiddenMode = false;
    private volatile MultiplayerScope multiplayerScope = MultiplayerScope.OFF;

    /** Dev-mode credential override. */
    public static CredentialOverride override;

    public static class CredentialOverride {
        public final UUID profileId;
        public final String accessToken;
        public final String userName;
        public CredentialOverride(UUID profileId, String accessToken, String userName) {
            this.profileId = profileId;
            this.accessToken = accessToken;
            this.userName = userName;
        }
    }

    /** Cached credentials snapshot. */
    public static class Snapshot {
        public final UUID profileId;
        public final String userName;
        public final String accessToken;
        public Snapshot(UUID profileId, String userName, String accessToken) {
            this.profileId = profileId;
            this.userName = userName;
            this.accessToken = accessToken;
        }
    }

    // ---- MinecraftBridge implementation ----

    @Override public UUID profileId() { return profileId0(); }
    @Override public String userName() { return userName0(); }
    @Override public String accessToken() { return accessToken0(); }
    @Override public boolean isHostingP2P() { return false; /* TODO */ }
    @Override public boolean isConnectedViaP2P() { return connectedViaP2P; }
    @Override public void setConnectedViaP2P(boolean c) { connectedViaP2P = c; }
    @Override public boolean friendsEnabled() { return true; }
    @Override public boolean allowFriendRequests() { return true; }
    @Override public boolean inGameNotificationsEnabled() { return true; }
    @Override public PresenceSharing presenceSharing() { return presenceSharingMode; }
    @Override public void setPresenceSharingMode(PresenceSharing m) { presenceSharingMode = m; }
    @Override public boolean hiddenMode() { return hiddenMode; }
    @Override public void setHiddenMode(boolean h) { hiddenMode = h; }
    @Override public MultiplayerScope multiplayerScope() { return multiplayerScope; }
    @Override public void setMultiplayerScope(MultiplayerScope s) { multiplayerScope = s; }

    @Override
    public boolean inLevel() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("getMinecraft").invoke(null);
            if (instance == null) return false;
            Field f = mc.getDeclaredField("world"); // MCP: Minecraft.world
            f.setAccessible(true);
            return f.get(instance) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void executeOnClientThread(Runnable r) {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("getMinecraft").invoke(null);
            if (instance != null) {
                mc.getMethod("addScheduledTask", Runnable.class).invoke(instance, r);
                return;
            }
        } catch (Throwable ignored) {}
        MAIN_THREAD.execute(new DelegatingRunnable(r));
    }

    @Override
    public void notifyToast(String type, String name, UUID profileId) {
        LOGGER.info("[toast] " + type + ": " + name + " (" + profileId + ")");
    }

    @Override
    public void disconnectFromCurrentWorld() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("getMinecraft").invoke(null);
            if (instance == null) return;
            // MCP: Minecraft.world is null means not connected
            Field worldField = mc.getDeclaredField("world");
            worldField.setAccessible(true);
            Object world = worldField.get(instance);
            if (world != null) {
                // MCP: Minecraft.loadWorld(null) to disconnect
                Method loadWorld = mc.getMethod("loadWorld",
                        Class.forName("net.minecraft.client.multiplayer.WorldClient"));
                loadWorld.invoke(instance, (Object) null);
            }
        } catch (Throwable t) {
            LOGGER.warn("[bridge] disconnectFromCurrentWorld failed", t);
        }
    }

    // ---- Credential fetching ----

    private UUID profileId0() {
        CredentialOverride o = override;
        if (o != null) return o.profileId;
        Snapshot s = tryFetchOrNull();
        return s != null ? s.profileId : new UUID(0, 0);
    }

    private String userName0() {
        CredentialOverride o = override;
        if (o != null) return o.userName;
        Snapshot s = tryFetchOrNull();
        return s != null ? s.userName : "Unknown";
    }

    private String accessToken0() {
        CredentialOverride o = override;
        if (o != null) return o.accessToken;
        Snapshot s = tryFetchOrNull();
        return s != null ? s.accessToken : "";
    }

    /**
     * Try to fetch credentials from the running Minecraft client.
     * Uses MCP names: Minecraft.getMinecraft() -> getSession() -> Session
     * Session: getUsername(), getPlayerID(), getToken()
     */
    public static Snapshot tryFetchOrNull() {
        CredentialOverride o = override;
        if (o != null) return new Snapshot(o.profileId, o.userName, o.accessToken);
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("getMinecraft").invoke(null);
            if (instance == null) return null;

            // MCP: Minecraft.getSession() returns net.minecraft.util.Session
            Method getSession = mc.getMethod("getSession");
            Object session = getSession.invoke(instance);
            if (session == null) return null;

            // MCP: Session.getUsername(), Session.getPlayerID(), Session.getToken()
            String userName = (String) session.getClass().getMethod("getUsername").invoke(session);
            String uuidStr = (String) session.getClass().getMethod("getPlayerID").invoke(session);
            String token = (String) session.getClass().getMethod("getToken").invoke(session);

            if (userName == null || uuidStr == null) return null;
            UUID uuid = UUID.fromString(uuidStr);
            return new Snapshot(uuid, userName, token != null ? token : "");
        } catch (Throwable t) {
            LOGGER.debug("[bridge] tryFetch failed: " + t.getMessage());
            return null;
        }
    }

    // ---- Named static inner classes (no lambdas) ----

    private static final class MainThreadExecutor implements Executor {
        @Override
        public void execute(Runnable r) {
            try { r.run(); }
            catch (Throwable t) { LOGGER.warn("[bridge] main-thread runnable threw", t); }
        }
    }

    private static final class DelegatingRunnable implements Runnable {
        private final Runnable delegate;
        DelegatingRunnable(Runnable delegate) { this.delegate = delegate; }
        @Override
        public void run() {
            try { delegate.run(); }
            catch (Throwable t) { LOGGER.warn("[bridge] main-thread runnable threw", t); }
        }
    }
}
