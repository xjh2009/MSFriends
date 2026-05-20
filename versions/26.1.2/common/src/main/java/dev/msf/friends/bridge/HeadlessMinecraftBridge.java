package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Reflection-driven {@link MinecraftBridge} implementation.
 *
 * <p>Pulls credentials from {@code Minecraft.getInstance().getUser()} and
 * everything else from a small set of getters. When MC isn't reachable
 * (eg. running outside the game for tests) all status getters return safe
 * defaults; {@code joinHost} / {@code acceptGuest} throw to make missing
 * wiring obvious to callers.
 */
public final class HeadlessMinecraftBridge implements MinecraftBridge {
    private static final Logger LOGGER = Logging.get();

    public record CredentialOverride(UUID profileId, String accessToken, String userName) {}

    /** Optional override populated via system properties for offline development. */
    public static volatile @Nullable CredentialOverride override;

    private final Executor mainThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "msf-mc-bridge"); t.setDaemon(true); return t;
    });

    /** Mutable presence sharing mode, default ALL. Can be changed from the web UI. */
    private volatile MinecraftBridge.PresenceSharing presenceSharingMode = MinecraftBridge.PresenceSharing.ALL;

    /** Local hidden-mode flag. When enabled we push OFFLINE once and suppress follow-up presence updates. */
    private volatile boolean hiddenMode;

    /** User-chosen multiplayer scope (OFF/LAN/ONLINE). Default OFF — user must opt-in. */
    private volatile MinecraftBridge.MultiplayerScope multiplayerScopeMode = MinecraftBridge.MultiplayerScope.OFF;

    /** True while this client is connected as a P2P guest (not the host). Cleared by GuestConnectionMixin on clearClientLevel. */
    private volatile boolean connectedViaP2P = false;

    @Override public boolean isConnectedViaP2P() { return connectedViaP2P; }
    @Override public void setConnectedViaP2P(boolean connected) { this.connectedViaP2P = connected; }

    @Override
    public void setPresenceSharingMode(MinecraftBridge.PresenceSharing mode) { this.presenceSharingMode = mode; }

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
        // Only report hosting P2P when the user has explicitly chosen ONLINE mode.
        // LAN-only and OFF modes do not activate the P2P/signaling system.
        return multiplayerScopeMode == MinecraftBridge.MultiplayerScope.ONLINE;
    }

    @Override public boolean inLevel() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("getInstance").invoke(null);
            if (instance == null) return false;
            java.lang.reflect.Field f = mc.getDeclaredField("level");
            f.setAccessible(true);
            return f.get(instance) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public void executeOnClientThread(Runnable r) {
        // Try to use the actual Minecraft.execute() if available; fall back to dedicated worker.
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("getInstance").invoke(null);
            if (instance != null) {
                // Minecraft implements Executor, call execute(Runnable)
                mc.getMethod("execute", Runnable.class).invoke(instance, r);
                return;
            }
        } catch (Throwable ignore) {}
        mainThread.execute(() -> {
            try { r.run(); }
            catch (Throwable t) { LOGGER.warn("[bridge] main-thread runnable threw", t); }
        });
    }

    @Override
    public void joinHost(io.netty.channel.Channel rtcChannel, dev.onvoid.webrtc.RTCPeerConnection peerConnection) {
        try {
            var creds = tryFetchOrThrow();
            // Mark this client as a P2P guest before dispatching connection so
            // the window title can show "在线" instead of "局域网".
            this.connectedViaP2P = true;
            executeOnClientThread(() -> {
                try {
                    ConnectionBridge.joinHost(rtcChannel, creds.userName(), creds.profileId());
                } catch (Throwable t) {
                    LOGGER.error("[bridge] joinHost failed", t);
                    dev.msf.friends.webrtc.RtcChannel.dispose(peerConnection, null);
                    // Show disconnect screen on failure
                    try {
                        Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
                        Object instance = mc.getMethod("getInstance").invoke(null);
                        mc.getMethod("setScreen", Class.forName("net.minecraft.client.gui.screens.Screen"))
                                .invoke(instance, (Object) null);
                    } catch (Throwable ignore) {}
                }
            });
        } catch (Throwable t) {
            LOGGER.error("[bridge] joinHost failed (no credentials)", t);
        }
    }

    @Override
    public void acceptGuest(io.netty.channel.Channel rtcChannel, UUID guestProfileId) {
        // Must run on the server thread
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("getInstance").invoke(null);
            if (instance == null) { rtcChannel.close(); return; }
            Object server = mc.getMethod("getSingleplayerServer").invoke(instance);
            if (server == null) { rtcChannel.close(); return; }
            // server.execute(Runnable) to run on server thread
            server.getClass().getMethod("execute", Runnable.class).invoke(server, (Runnable) () -> {
                try {
                    AcceptGuestBridge.acceptGuest(rtcChannel, guestProfileId);
                } catch (Throwable t) {
                    LOGGER.error("[bridge] acceptGuest failed", t);
                    rtcChannel.close();
                }
            });
        } catch (Throwable t) {
            LOGGER.error("[bridge] acceptGuest dispatch failed", t);
            rtcChannel.close();
        }
    }

    @Override public void notifyToast(String type, String name, @Nullable UUID profileId) {
        LOGGER.info("[toast] {} name={} profile={}", type, name, profileId);
        // Show in-game toast (achievement-style popup) on the render thread
        executeOnClientThread(() -> ToastBridge.showToast(type, name, profileId));
    }

    public static Optional<Snapshot> tryFetch() {
        CredentialOverride o = override;
        if (o != null) return Optional.of(new Snapshot(o.profileId, o.userName, o.accessToken));
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("getInstance").invoke(null);
            if (instance == null) {
                LOGGER.trace("[bridge] Minecraft.getInstance() returned null");
                return Optional.empty();
            }
            Object user;
            try { user = mc.getMethod("getUser").invoke(instance); }
            catch (NoSuchMethodException e) {
                user = findZeroArg(mc, instance, "User");
            }
            if (user == null) {
                LOGGER.trace("[bridge] getUser() returned null");
                return Optional.empty();
            }
            String token = invokeFirstString(user, "getAccessToken", "accessToken");
            String name  = invokeFirstString(user, "getName", "name");
            // getProfileId() returns UUID directly; getUuid() does not exist in 26.1.2
            UUID profileId = null;
            try {
                Object idObj = user.getClass().getMethod("getProfileId").invoke(user);
                if (idObj instanceof UUID u) profileId = u;
                else if (idObj != null) profileId = UUID.fromString(idObj.toString());
            } catch (NoSuchMethodException e) {
                // Fallback: try older method names
                String uuidStr = invokeFirstString(user, "getUuid", "uuid");
                profileId = UUID.fromString(uuidStr);
            }
            if (profileId == null) return Optional.empty();
            return Optional.of(new Snapshot(profileId, name, token));
        } catch (Throwable t) {
            LOGGER.debug("[bridge] reflective fetch unavailable: {}", t.toString());
            return Optional.empty();
        }
    }

    private static Snapshot tryFetchOrThrow() {
        return tryFetch().orElseThrow(() ->
                new IllegalStateException("Minecraft client not available; supply -Dmsf.dev.* overrides in dev runs"));
    }

    @Nullable
    private static Object findZeroArg(Class<?> declaring, Object target, String returnTypeSimpleName) throws Exception {
        for (var m : declaring.getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals(returnTypeSimpleName)) {
                return m.invoke(target);
            }
        }
        return null;
    }

    private static String invokeFirstString(Object target, String... candidates) throws Exception {
        for (String name : candidates) {
            try {
                Object v = target.getClass().getMethod(name).invoke(target);
                if (v != null) return v.toString();
            } catch (NoSuchMethodException ignore) {}
        }
        throw new NoSuchMethodException(String.join("/", candidates) + " on " + target.getClass());
    }

    public record Snapshot(UUID profileId, String userName, String accessToken) {}
}
