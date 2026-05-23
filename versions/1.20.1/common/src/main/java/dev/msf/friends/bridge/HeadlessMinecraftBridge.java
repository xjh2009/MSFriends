package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import dev.onvoid.webrtc.RTCPeerConnection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Reflection-driven {@link MinecraftBridge} implementation for MC 1.20.1.
 *
 * <p>Pulls credentials from {@code MinecraftClient.getInstance().getSession()}
 * which returns a {@code Session} object (Yarn: {@code net.minecraft.client.util.Session}).
 *
 * <p>Functional logic mirrors 26.1.2 exactly: presence sharing, hidden mode,
 * multiplayer scope, P2P guest tracking, joinHost/acceptGuest dispatch,
 * toast notifications, and credential fetching.
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

    /** True while this client is connected as a P2P guest (not the host). Cleared by GuestConnectionMixin on disconnect. */
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
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) return false;
            java.lang.reflect.Field f = FabricReflect.mcField(mc, "world");
            f.setAccessible(true);
            return f.get(instance) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public void executeOnClientThread(Runnable r) {
        // Try to use the actual MinecraftClient.execute() if available; fall back to dedicated worker.
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance != null) {
                // MinecraftClient implements Executor, call execute(Runnable)
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
    public void joinHost(io.netty.channel.Channel rtcChannel, RTCPeerConnection peerConnection) {
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
                        Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
                        Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
                        mc.getMethod("setScreen", FabricReflect.mcClass("net.minecraft.client.gui.screen.Screen"))
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
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) { rtcChannel.close(); return; }
            Object server = FabricReflect.mcMethod(mc, "getServer").invoke(instance);
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

    // ------------------------------------------------------------------ credentials

    public record Snapshot(UUID profileId, String userName, String accessToken) {}

    private Snapshot tryFetchOrThrow() {
        return tryFetch().orElseThrow(() ->
                new IllegalStateException("Minecraft client not available; supply -Dmsf.dev.* overrides in dev runs"));
    }

    public static Optional<Snapshot> tryFetch() {
        CredentialOverride o = override;
        if (o != null) return Optional.of(new Snapshot(o.profileId, o.userName, o.accessToken));
        try {
            Class<?> mc = FabricReflect.mcClass("net.minecraft.client.MinecraftClient");
            Object instance = FabricReflect.mcMethod(mc, "getInstance").invoke(null);
            if (instance == null) {
                LOGGER.trace("[bridge] MinecraftClient.getInstance() returned null");
                return Optional.empty();
            }

            // 1.20.1: MinecraftClient.session → Session object
            Field sessionField = FabricReflect.mcField(mc, "session");
            sessionField.setAccessible(true);
            Object session = sessionField.get(instance);
            if (session == null) {
                LOGGER.trace("[bridge] session field returned null");
                return Optional.empty();
            }

            // Session.getAccessToken()
            Method getAccessToken = FabricReflect.mcMethod(session.getClass(), "getAccessToken");
            String accessToken = (String) getAccessToken.invoke(session);

            // Session.getProfile() → GameProfile
            Method getProfile = session.getClass().getMethod("getProfile");
            Object profile = getProfile.invoke(session);
            if (profile == null) return Optional.empty();

            UUID profileId = (UUID) profile.getClass().getMethod("getId").invoke(profile);
            String name = (String) profile.getClass().getMethod("getName").invoke(profile);

            return Optional.of(new Snapshot(profileId, name, accessToken));
        } catch (Throwable t) {
            LOGGER.debug("[bridge] reflective fetch unavailable: {}", t.toString());
            return Optional.empty();
        }
    }
}
