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
 * Reflection-driven MinecraftBridge for MC 1.17.1.
 * Same as 1.18.2 — authlib 3.x.
 */
public final class HeadlessMinecraftBridge implements MinecraftBridge {
    private static final Logger LOGGER = Logging.get();

    public record CredentialOverride(UUID profileId, String accessToken, String userName) {}

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
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) return false;
            Field f;
            try { f = mc.getDeclaredField("field_1687"); }
            catch (NoSuchFieldException e) { f = FabricReflect.mcField(mc, "level"); }
            f.setAccessible(true);
            return f.get(instance) != null;
        } catch (Throwable t) { return false; }
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
        } catch (Throwable t) { LOGGER.error("[bridge] joinHost failed (no credentials)", t); }
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
                try { mc.getMethod("disconnect").invoke(instance); }
                catch (NoSuchMethodException e) {
                    try { mc.getMethod("clearLevel").invoke(instance); }
                    catch (NoSuchMethodException e2) { LOGGER.warn("[bridge] cannot disconnect"); }
                }
            }
        } catch (Throwable t) { LOGGER.warn("[bridge] disconnectFromCurrentWorld failed", t); }
    }

    public static Optional<Snapshot> tryFetch() {
        CredentialOverride o = override;
        if (o != null) return Optional.of(new Snapshot(o.profileId, o.userName, o.accessToken));
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.Minecraft");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) { return Optional.empty(); }
            Object session;
            try { session = FabricReflect.mcMethod(mc, "getUser").invoke(instance); }
            catch (NoSuchMethodException e) {
                try { session = FabricReflect.mcMethod(mc, "getSession").invoke(instance); }
                catch (NoSuchMethodException e2) {
                    session = findZeroArg(mc, instance, "Session");
                    if (session == null) session = findZeroArg(mc, instance, "User");
                }
            }
            if (session == null) { return Optional.empty(); }

            String token = invokeUserMethod(session, "getAccessToken");
            String name  = invokeUserMethod(session, "getName");

            UUID profileId = null;
            try {
                String uuidStr = invokeUserMethod(session, "getUuid");
                if (uuidStr != null) profileId = UUID.fromString(uuidStr);
            } catch (Exception e) {
                LOGGER.debug("[bridge] getUuid() failed, trying uuid field");
                try {
                    Field uuidField = FabricReflect.mcField(session.getClass(), "uuid");
                    uuidField.setAccessible(true);
                    Object uuidObj = uuidField.get(session);
                    if (uuidObj instanceof UUID u) profileId = u;
                    else if (uuidObj != null) profileId = UUID.fromString(uuidObj.toString());
                } catch (NoSuchFieldException e2) {
                    try {
                        Field uuidField = session.getClass().getDeclaredField("field_1985");
                        uuidField.setAccessible(true);
                        Object uuidObj = uuidField.get(session);
                        if (uuidObj instanceof UUID u) profileId = u;
                        else if (uuidObj != null) profileId = UUID.fromString(uuidObj.toString());
                    } catch (Exception e3) { /* ignore */ }
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
                new IllegalStateException("Minecraft client not available; supply -Dmsf.dev.* overrides"));
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
            try {
                String intermediaryName = switch (mojangMethod) {
                    case "getAccessToken" -> "method_1674";
                    case "getName", "getUsername" -> "method_1676";
                    case "getUuid" -> "method_1673";
                    default -> mojangMethod;
                };
                Method m = user.getClass().getMethod(intermediaryName);
                Object v = m.invoke(user);
                return v != null ? v.toString() : null;
            } catch (NoSuchMethodException e2) {
                try {
                    String fieldName = mojangMethod.replace("get", "");
                    fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                    Field f = FabricReflect.mcField(user.getClass(), fieldName);
                    f.setAccessible(true);
                    Object v = f.get(user);
                    return v != null ? v.toString() : null;
                } catch (NoSuchFieldException e3) {
                    String fieldName = switch (mojangMethod) {
                        case "getAccessToken" -> "field_1983";
                        case "getName", "getUsername" -> "field_1982";
                        case "getUuid" -> "field_1985";
                        default -> mojangMethod;
                    };
                    Field f = user.getClass().getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object v = f.get(user);
                    return v != null ? v.toString() : null;
                }
            }
        }
    }

    public record Snapshot(UUID profileId, String userName, String accessToken) {}
}
