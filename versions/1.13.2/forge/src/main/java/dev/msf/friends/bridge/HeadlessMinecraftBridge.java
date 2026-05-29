package dev.msf.friends.bridge;

import dev.onvoid.webrtc.RTCPeerConnection;
import io.netty.channel.Channel;
import dev.msf.friends.util.Logging;
import org.slf4j.Logger;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Reflection-driven {@link MinecraftBridge} for MC 1.13.2 Forge (MCP mappings).
 *
 * <p>Uses {@link ForgeReflect} to access MC internals with SRG fallback for
 * production. All method and field names are 1.13.2 MCP-mapped.
 *
 * <p>Key MCP names for 1.13.2:
 * <ul>
 *   <li>{@code Minecraft.getInstance()} → {@code func_71410_x}</li>
 *   <li>{@code Minecraft.getIntegratedServer()} → {@code func_71401_C}</li>
 *   <li>{@code Minecraft.displayGuiScreen(Screen)} → {@code func_147108_a}</li>
 *   <li>{@code Minecraft.session} → {@code field_71449_j}</li>
 *   <li>{@code Minecraft.world} → {@code field_71441_e}</li>
 * </ul>
 */
public final class HeadlessMinecraftBridge implements MinecraftBridge {
    private static final Logger LOGGER = Logging.get();

    /** Dev-mode credential override (set via -Dmsf.dev.* system properties). */
    public static volatile @Nullable CredentialOverride override;

    private final Executor mainThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "msf-mc-bridge");
        t.setDaemon(true);
        return t;
    });

    private volatile MinecraftBridge.PresenceSharing presenceSharingMode = MinecraftBridge.PresenceSharing.ALL;
    private volatile boolean hiddenMode;
    private volatile MinecraftBridge.MultiplayerScope multiplayerScopeMode = MinecraftBridge.MultiplayerScope.OFF;
    private volatile boolean connectedViaP2P = false;

    // ---- MinecraftBridge implementation ----

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
        return multiplayerScopeMode == MinecraftBridge.MultiplayerScope.ONLINE;
    }

    @Override
    public boolean inLevel() {
        try {
            Object mc = getMinecraftInstance();
            if (mc == null) return false;
            Class<?> mcClass = ForgeReflect.mcClass("net.minecraft.client.Minecraft");
            Field worldField = ForgeReflect.mcField(mcClass, "world");
            worldField.setAccessible(true);
            return worldField.get(mc) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void executeOnClientThread(Runnable r) {
        try {
            Object mc = getMinecraftInstance();
            if (mc != null) {
                Class<?> mcClass = ForgeReflect.mcClass("net.minecraft.client.Minecraft");
                ForgeReflect.mcMethod(mcClass, "addScheduledTask", Runnable.class).invoke(mc, r);
                return;
            }
        } catch (Throwable ignored) {}
        mainThread.execute(() -> {
            try { r.run(); }
            catch (Throwable t) { LOGGER.warn("[bridge] main-thread runnable threw", t); }
        });
    }

    @Override
    public void joinHost(Channel rtcChannel, RTCPeerConnection peerConnection) {
        try {
            Snapshot creds = tryFetchOrThrow();
            this.connectedViaP2P = true;
            executeOnClientThread(() -> {
                try {
                    ConnectionBridge.joinHost(rtcChannel, creds.userName, creds.profileId);
                } catch (Throwable t) {
                    LOGGER.error("[bridge] joinHost failed", t);
                    dev.msf.friends.webrtc.RtcChannel.dispose(peerConnection, null);
                    // Try to close any screen
                    try {
                        Object mc = getMinecraftInstance();
                        if (mc != null) {
                            Class<?> mcClass = ForgeReflect.mcClass("net.minecraft.client.Minecraft");
                            ForgeReflect.mcMethod(mcClass, "displayGuiScreen",
                                    ForgeReflect.mcClass("net.minecraft.client.gui.screen.Screen"))
                                    .invoke(mc, (Object) null);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            LOGGER.error("[bridge] joinHost failed (no credentials)", t);
        }
    }

    @Override
    public void acceptGuest(Channel rtcChannel, UUID guestProfileId) {
        try {
            Object mc = getMinecraftInstance();
            if (mc == null) { rtcChannel.close(); return; }
            Class<?> mcClass = ForgeReflect.mcClass("net.minecraft.client.Minecraft");
            Object server = ForgeReflect.mcMethod(mcClass, "getIntegratedServer").invoke(mc);
            if (server == null) { rtcChannel.close(); return; }
            Class<?> serverClass = ForgeReflect.mcClass("net.minecraft.server.MinecraftServer");
            ForgeReflect.mcMethod(serverClass, "addScheduledTask", Runnable.class).invoke(server, (Runnable) () -> {
                try {
                    AcceptGuestBridge.acceptGuest(rtcChannel, guestProfileId);
                } catch (Throwable t) {
                    LOGGER.error("[bridge] acceptGuest failed", t);
                    rtcChannel.close();
                }
            });
        } catch (Throwable t) {
            LOGGER.error("[bridge] acceptGuest setup failed", t);
            rtcChannel.close();
        }
    }

    @Override
    public void notifyToast(String type, String name, @Nullable UUID profileId) {
        LOGGER.info("[toast] {} name={} profile={}", type, name, profileId);
        executeOnClientThread(() -> ToastBridge.showToast(type, name, profileId));
    }

    @Override
    public void disconnectFromCurrentWorld() {
        try {
            Object mc = getMinecraftInstance();
            if (mc == null) return;
            Class<?> mcClass = ForgeReflect.mcClass("net.minecraft.client.Minecraft");
            Field worldField = ForgeReflect.mcField(mcClass, "world");
            worldField.setAccessible(true);
            if (worldField.get(mc) != null) {
                // Try disconnect() first, then clearLevel()
                try {
                    ForgeReflect.mcMethod(mcClass, "disconnect").invoke(mc);
                } catch (NoSuchMethodException e) {
                    try {
                        ForgeReflect.mcMethod(mcClass, "clearLevel").invoke(mc);
                    } catch (NoSuchMethodException e2) {
                        ForgeReflect.mcMethod(mcClass, "loadWorld",
                                ForgeReflect.mcClass("net.minecraft.client.multiplayer.WorldClient"))
                                .invoke(mc, (Object) null);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[bridge] disconnectFromCurrentWorld failed", t);
        }
    }

    // ---- Credential fetching ----

    /**
     * Try to fetch credentials from the running Minecraft client.
     * Uses MCP names: {@code Minecraft.getInstance()} → {@code getSession()} → {@code Session}
     * Session: {@code getUsername()}, {@code getPlayerID()}, {@code getToken()}
     */
    public static Optional<Snapshot> tryFetch() {
        CredentialOverride o = override;
        if (o != null) return Optional.of(new Snapshot(o.profileId, o.userName, o.accessToken));
        try {
            Object mc = getMinecraftInstance();
            if (mc == null) return Optional.empty();

            Class<?> mcClass = ForgeReflect.mcClass("net.minecraft.client.Minecraft");
            Method getSession = ForgeReflect.mcMethod(mcClass, "getSession");
            Object session = getSession.invoke(mc);
            if (session == null) return Optional.empty();

            Class<?> sessionClass = session.getClass();
            String userName = (String) ForgeReflect.mcMethod(sessionClass, "getUsername").invoke(session);
            String uuidStr = (String) ForgeReflect.mcMethod(sessionClass, "getPlayerID").invoke(session);
            String token = (String) ForgeReflect.mcMethod(sessionClass, "getToken").invoke(session);

            if (userName == null || uuidStr == null) return Optional.empty();
            UUID uuid = UUID.fromString(uuidStr);
            return Optional.of(new Snapshot(uuid, userName, token != null ? token : ""));
        } catch (Throwable t) {
            LOGGER.trace("[bridge] tryFetch failed: {}", t.getMessage());
            return Optional.empty();
        }
    }

    static Snapshot tryFetchOrThrow() {
        return tryFetch().orElseThrow(() ->
                new IllegalStateException("Minecraft client not available; supply -Dmsf.dev.* overrides in dev runs"));
    }

    private static Object getMinecraftInstance() throws Exception {
        return ForgeReflect.mcMethod(ForgeReflect.mcClass("net.minecraft.client.Minecraft"), "getInstance").invoke(null);
    }

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
}
