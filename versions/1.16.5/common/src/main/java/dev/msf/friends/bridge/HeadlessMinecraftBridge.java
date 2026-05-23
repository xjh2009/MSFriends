package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) return false;
            Field f = FabricReflect.mcField(mc, "world");
            f.setAccessible(true);
            return f.get(instance) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public void executeOnClientThread(Runnable r) {
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
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
                        Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
                        Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
                        FabricReflect.mcMethod(mc, "openScreen",
                                FabricReflect.mcClass("net.minecraft.client.gui.screen.Screen"))
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
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) { rtcChannel.close(); return; }
            Object server = FabricReflect.mcMethod(mc, "getServer").invoke(instance);
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
        LOGGER.info("[toast] type={} name={} profile={}", type, name, profileId);
        executeOnClientThread(() -> ToastBridge.showToast(type, name, profileId));
    }

    @Override public void disconnectFromCurrentWorld() {
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance != null) {
                // 1.16.5: use disconnect() - equivalent of 26.1.2's disconnectWithProgressScreen(false)
                try {
                    mc.getMethod("disconnect").invoke(instance);
                } catch (NoSuchMethodException e) {
                    // Fallback: clear world reference directly
                    try {
                        Field worldField = FabricReflect.mcField(mc, "world");
                        worldField.setAccessible(true);
                        worldField.set(instance, null);
                    } catch (NoSuchFieldException e2) {
                        LOGGER.warn("[bridge] cannot disconnect");
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
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) {
                LOGGER.info("[bridge] MinecraftClient.getInstance() returned null");
                return Optional.empty();
            }
            Object session;
            try {
                session = FabricReflect.mcMethod(mc, "getSession").invoke(instance);
            } catch (NoSuchMethodException e) {
                session = findZeroArgReturning(mc, instance, "Session");
            }
            if (session == null) {
                LOGGER.info("[bridge] getSession() returned null");
                return Optional.empty();
            }

            String token = invokeUserMethod(session, "getAccessToken");
            String name  = invokeUserMethod(session, "getUsername");

            UUID profileId = null;
            try {
                Method getProfileId = FabricReflect.mcMethod(session.getClass(), "getProfileId");
                Object idObj = getProfileId.invoke(session);
                if (idObj instanceof UUID) profileId = (UUID) idObj;
                else if (idObj != null) profileId = UUID.fromString(idObj.toString());
            } catch (NoSuchMethodException e) {
                try {
                    String uuidStr = invokeUserMethod(session, "getUuid");
                    if (uuidStr != null) profileId = UUID.fromString(uuidStr);
                } catch (Exception e2) {
                    try {
                        Field uuidField = FabricReflect.mcField(session.getClass(), "uuid");
                        uuidField.setAccessible(true);
                        Object uuidObj = uuidField.get(session);
                        if (uuidObj instanceof UUID) profileId = (UUID) uuidObj;
                        else if (uuidObj != null) profileId = UUID.fromString(uuidObj.toString());
                    } catch (Exception ignored) {}
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
    private static Object findZeroArgReturning(Class<?> declaring, Object target, String returnTypeSimpleName) throws Exception {
        for (Method m : declaring.getMethods()) {
            if (m.getParameterCount() == 0) {
                String simpleName = m.getReturnType().getSimpleName();
                if (simpleName.equals(returnTypeSimpleName) || simpleName.contains(returnTypeSimpleName)) {
                    return m.invoke(target);
                }
            }
        }
        return null;
    }

    private static String invokeUserMethod(Object session, String namedMethod) throws Exception {
        try {
            Method m = FabricReflect.mcMethod(session.getClass(), namedMethod);
            Object v = m.invoke(session);
            return v != null ? v.toString() : null;
        } catch (NoSuchMethodException e) {
            String fieldName = namedMethod.replace("get", "");
            fieldName = fieldName.substring(0, 1).toLowerCase(Locale.ROOT) + fieldName.substring(1);
            try {
                Field f = FabricReflect.mcField(session.getClass(), fieldName);
                f.setAccessible(true);
                Object v = f.get(session);
                return v != null ? v.toString() : null;
            } catch (NoSuchFieldException e2) {
                throw e;
            }
        }
    }

    public record Snapshot(UUID profileId, String userName, String accessToken) {}
}