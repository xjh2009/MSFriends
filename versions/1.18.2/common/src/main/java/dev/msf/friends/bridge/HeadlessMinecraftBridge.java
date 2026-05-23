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
 * Reflection-driven {@link MinecraftBridge} implementation for MC 1.18.2.
 *
 * <p>Pulls credentials from {@code Minecraft.getInstance().getUser()} and
 * everything else from a small set of getters.
 *
 * <p>1.18.2 differences from newer versions:
 * <ul>
 *   <li>User class has {@code getUuid()} returning a String, not {@code getProfileId()} returning UUID</li>
 *   <li>No {@code disconnectWithProgressScreen} method</li>
 *   <li>No {@code BandwidthDebugMonitor}</li>
 *   <li>Simplified connection bridge (no advanced features)</li>
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

    /** Mutable presence sharing mode, default ALL. */
    private volatile MinecraftBridge.PresenceSharing presenceSharingMode = MinecraftBridge.PresenceSharing.ALL;

    /** Local hidden-mode flag. */
    private volatile boolean hiddenMode;

    /** User-chosen multiplayer scope (OFF/LAN/ONLINE). Default OFF. */
    private volatile MinecraftBridge.MultiplayerScope multiplayerScopeMode = MinecraftBridge.MultiplayerScope.OFF;

    /** True while this client is connected as a P2P guest. */
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
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) return false;
            Field f = FabricReflect.mcField(mc, "level");
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
            this.connectedViaP2P = true;
            executeOnClientThread(() -> {
                try {
                    ConnectionBridge.joinHost(rtcChannel, creds.userName(), creds.profileId());
                } catch (Throwable t) {
                    LOGGER.error("[bridge] joinHost failed", t);
                    dev.msf.friends.webrtc.RtcChannel.dispose(peerConnection, null);
                    try {
                        Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
                        Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
                        FabricReflect.mcMethod(mc, "setScreen", FabricReflect.mcClass("net.minecraft.client.gui.screens.Screen"))
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
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) { rtcChannel.close(); return; }
            Object server = FabricReflect.mcMethod(mc, "getSingleplayerServer").invoke(instance);
            if (server == null) { rtcChannel.close(); return; }
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
        executeOnClientThread(() -> ToastBridge.showToast(type, name, profileId));
    }

    @Override public void disconnectFromCurrentWorld() {
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance != null) {
                try {
                    mc.getMethod("disconnect").invoke(instance);
                } catch (NoSuchMethodException e) {
                    try {
                        mc.getMethod("clearLevel").invoke(instance);
                    } catch (NoSuchMethodException e2) {
                        LOGGER.warn("[bridge] cannot disconnect: no disconnect/clearLevel method");
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[bridge] disconnectFromCurrentWorld failed", t);
        }
    }

    public static Optional<Snapshot> tryFetch() {
        CredentialOverride o = override;
        if (o != null) return Optional.of(new Snapshot(o.profileId, o.userName, o.accessToken));
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) {
                LOGGER.info("[bridge] Minecraft.getInstance() returned null (not yet initialised)");
                return Optional.empty();
            }
            Object user;
            try { user = FabricReflect.mcMethod(mc, "getUser").invoke(instance); }
            catch (NoSuchMethodException e) {
                user = findZeroArg(mc, instance, "User");
            }
            if (user == null) {
                LOGGER.trace("[bridge] getUser() returned null");
                return Optional.empty();
            }

            String token = invokeFirstString(user, "getAccessToken", "accessToken");
            String name  = invokeFirstString(user, "getName", "name");
            // 1.18.2: User has getUuid() returning String, newer versions have getProfileId() returning UUID
            UUID profileId = null;
            try {
                // Try getProfileId first (newer authlib on some 1.18.2 builds)
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
            if (m.getParameterCount() == 0) {
                String simpleName = m.getReturnType().getSimpleName();
                if (simpleName.equals(returnTypeSimpleName) ||
                    matchesIntermediaryClass(simpleName, returnTypeSimpleName)) {
                    return m.invoke(target);
                }
            }
        }
        return null;
    }

    private static boolean matchesIntermediaryClass(String simpleName, String mojangName) {
        return switch (mojangName) {
            case "User" -> simpleName.equals("class_320");
            case "Minecraft" -> simpleName.equals("class_310");
            case "Connection" -> simpleName.equals("class_2535");
            default -> false;
        };
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
