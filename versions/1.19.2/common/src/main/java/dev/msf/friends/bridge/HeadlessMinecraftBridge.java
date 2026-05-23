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
 * Reflection-driven {@link MinecraftBridge} implementation for MC 1.19.2.
 *
 * <p>Pulls credentials from {@code Minecraft.getInstance().getUser()} and
 * everything else from a small set of getters. When MC isn't reachable
 * (eg. running outside the game for tests) all status getters return safe
 * defaults; {@code joinHost} / {@code acceptGuest} throw to make missing
 * wiring obvious to callers.
 *
 * <p>1.19.2 differences from 1.21.11:
 * <ul>
 *   <li>User class has {@code getUuid()} returning a String, not {@code getProfileId()} returning UUID</li>
 *   <li>No {@code disconnectWithProgressScreen} method</li>
 *   <li>No {@code BandwidthDebugMonitor}</li>
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

    /** Mutable presence sharing mode, default ALL. Can be changed from the web UI. */
    private volatile MinecraftBridge.PresenceSharing presenceSharingMode = MinecraftBridge.PresenceSharing.ALL;

    /** Local hidden-mode flag. */
    private volatile boolean hiddenMode;

    /** User-chosen multiplayer scope (OFF/LAN/ONLINE). Default OFF — user must opt-in. */
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
        // 1.19.2: use level = null + clear level approach
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance != null) {
                // Try disconnect() or clearLevel() method
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
                LOGGER.debug("[bridge] Minecraft.getInstance() returned null (not yet initialised)");
                return Optional.empty();
            }
            Object session;
            // 1.19.2 Yarn: the method is called getSession (not getUser)
            try { session = FabricReflect.mcMethod(mc, "getUser").invoke(instance); }
            catch (NoSuchMethodException e) {
                try { session = FabricReflect.mcMethod(mc, "getSession").invoke(instance); }
                catch (NoSuchMethodException e2) {
                    session = findZeroArg(mc, instance, "Session");
                    if (session == null) session = findZeroArg(mc, instance, "User");
                }
            }
            if (session == null) {
                LOGGER.debug("[bridge] getSession()/getUser() returned null (not logged in yet)");
                return Optional.empty();
            }

            String token = invokeUserMethod(session, "getAccessToken");
            String name  = invokeUserMethod(session, "getName");

            // In 1.19.2, Session has getUuid() returning String AND getUuidOrNull() returning UUID
            UUID profileId = null;
            try {
                // Try getUuidOrNull() first (returns UUID directly)
                Method getUuidOrNull;
                try {
                    getUuidOrNull = FabricReflect.mcMethod(session.getClass(), "getUuidOrNull");
                } catch (NoSuchMethodException e) {
                    // Direct intermediary fallback
                    getUuidOrNull = session.getClass().getMethod("method_44717");
                }
                Object idObj = getUuidOrNull.invoke(session);
                if (idObj instanceof UUID u) profileId = u;
                else if (idObj != null) profileId = UUID.fromString(idObj.toString());
            } catch (NoSuchMethodException e) {
                // Fallback: getUuid() returns a String UUID
                LOGGER.debug("[bridge] getUuidOrNull() not found, trying getUuid()");
                try {
                    String uuidStr = invokeUserMethod(session, "getUuid");
                    if (uuidStr != null) profileId = UUID.fromString(uuidStr);
                } catch (Exception e2) {
                    // Last resort: try uuid field directly
                    LOGGER.debug("[bridge] getUuid() failed, trying uuid field");
                    try {
                        Field uuidField = FabricReflect.mcField(session.getClass(), "uuid");
                        uuidField.setAccessible(true);
                        Object uuidObj = uuidField.get(session);
                        if (uuidObj instanceof UUID u) profileId = u;
                        else if (uuidObj != null) profileId = UUID.fromString(uuidObj.toString());
                    } catch (NoSuchFieldException e3) {
                        try {
                            Field uuidField = session.getClass().getDeclaredField("field_1985");
                            uuidField.setAccessible(true);
                            Object uuidObj = uuidField.get(session);
                            if (uuidObj instanceof UUID u) profileId = u;
                            else if (uuidObj != null) profileId = UUID.fromString(uuidObj.toString());
                        } catch (Exception e4) {
                            LOGGER.debug("[bridge] uuid field not found either");
                        }
                    }
                }
            }
            if (profileId == null) return Optional.empty();
            return Optional.of(new Snapshot(profileId, name, token));
        } catch (Throwable t) {
            LOGGER.warn("[bridge] reflective fetch unavailable: {}", t.toString());
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
            case "User", "Session" -> simpleName.equals("class_320") || simpleName.equals("Session");
            case "Minecraft", "MinecraftClient" -> simpleName.equals("class_310") || simpleName.equals("MinecraftClient");
            case "Connection", "ClientConnection" -> simpleName.equals("class_2535") || simpleName.equals("ClientConnection");
            default -> false;
        };
    }

    private static String invokeUserMethod(Object user, String mojangMethod) throws Exception {
        try {
            Method m = FabricReflect.mcMethod(user.getClass(), mojangMethod);
            Object v = m.invoke(user);
            return v != null ? v.toString() : null;
        } catch (NoSuchMethodException e) {
            // Fallback: try intermediary method names directly (production runtime uses intermediary, not Yarn)
            try {
                String intermediaryName = switch (mojangMethod) {
                    case "getAccessToken" -> "method_1674";
                    case "getName", "getUsername" -> "method_1676";
                    case "getUuid" -> "method_1673";
                    case "getUuidOrNull" -> "method_44717";
                    default -> mojangMethod;
                };
                Method m = user.getClass().getMethod(intermediaryName);
                Object v = m.invoke(user);
                return v != null ? v.toString() : null;
            } catch (NoSuchMethodException e2) {
                // Try field access via FabricReflect
                try {
                    String fieldName = mojangMethod.replace("get", "");
                    fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                    Field f = FabricReflect.mcField(user.getClass(), fieldName);
                    f.setAccessible(true);
                    Object v = f.get(user);
                    return v != null ? v.toString() : null;
                } catch (NoSuchFieldException e3) {
                    // Last resort: try intermediary field names directly
                    String fieldName = switch (mojangMethod) {
                        case "getAccessToken" -> "field_1983";
                        case "getName", "getUsername" -> "field_1982";
                        case "getUuid" -> "field_1985";
                        default -> mojangMethod;
                    };
                    try {
                        Field f = user.getClass().getDeclaredField(fieldName);
                        f.setAccessible(true);
                        Object v = f.get(user);
                        return v != null ? v.toString() : null;
                    } catch (NoSuchFieldException e4) {
                        throw e;
                    }
                }
            }
        }
    }

    public record Snapshot(UUID profileId, String userName, String accessToken) {}
}
