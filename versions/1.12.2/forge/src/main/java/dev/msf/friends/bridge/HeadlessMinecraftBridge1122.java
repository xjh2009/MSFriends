package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import dev.onvoid.webrtc.RTCPeerConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * MinecraftBridge implementation for Forge 1.12.2. Uses MCP mappings.
 *
 * Key mapping differences from Yarn 1.19.2:
 * - MinecraftClient → Minecraft.getMinecraft()
 * - Session → Minecraft.getMinecraft().getSession() (same name, different API)
 * - mc.world → mc.world (same in 1.12.2)
 * - mc.getServer() → same
 * - mc.disconnect() → mc.world.sendQuittingDisconnectingPacket() or mc.loadWorld(null)
 */
public final class HeadlessMinecraftBridge1122 implements MinecraftBridge {
    private static final Logger LOGGER = Logging.get();

    private final Executor mainThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "msf-mc-bridge");
        t.setDaemon(true);
        return t;
    });

    private volatile PresenceSharing presenceSharingMode = PresenceSharing.ALL;
    private volatile boolean hiddenMode;
    private volatile MultiplayerScope multiplayerScopeMode = MultiplayerScope.OFF;
    private volatile boolean connectedViaP2P = false;

    // --- Credential snapshot ---
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

    /** Optional credential override for dev environment. */
    public static volatile CredentialOverride override;

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

    @Override public boolean isConnectedViaP2P() { return connectedViaP2P; }
    @Override public void setConnectedViaP2P(boolean connected) { this.connectedViaP2P = connected; }
    @Override public void setPresenceSharingMode(PresenceSharing mode) { this.presenceSharingMode = mode; }
    @Override public PresenceSharing presenceSharing() { return presenceSharingMode; }
    @Override public boolean hiddenMode() { return hiddenMode; }

    @Override
    public void setHiddenMode(boolean hidden) {
        LOGGER.info("[bridge] hidden mode changed: {} -> {}", hiddenMode, hidden);
        this.hiddenMode = hidden;
    }

    @Override public MultiplayerScope multiplayerScope() { return multiplayerScopeMode; }

    @Override public boolean friendsEnabled() { return true; }
    @Override public boolean allowFriendRequests() { return true; }
    @Override public boolean inGameNotificationsEnabled() { return true; }

    @Override
    public void setMultiplayerScope(MultiplayerScope scope) {
        LOGGER.info("[bridge] multiplayer scope changed: {} -> {}", multiplayerScopeMode, scope);
        this.multiplayerScopeMode = scope;
    }

    @Override public UUID profileId() { return tryFetchOrThrow().profileId; }
    @Override public String userName() { return tryFetchOrThrow().userName; }
    @Override public String accessToken() { return tryFetchOrThrow().accessToken; }

    @Override
    public boolean isHostingP2P() {
        return multiplayerScopeMode == MultiplayerScope.ONLINE;
    }

    @Override
    public boolean inLevel() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.world != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void executeOnClientThread(Runnable r) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.addScheduledTask(r);
                return;
            }
        } catch (Throwable ignore) {}
        mainThread.execute(new Runnable() {
            @Override
            public void run() {
                try { r.run(); }
                catch (Throwable t) { LOGGER.warn("[bridge] main-thread runnable threw", t); }
            }
        });
    }

    @Override
    public void joinHost(RtcChannel rtcChannel, RTCPeerConnection peerConnection) {
        try {
            final Snapshot creds = tryFetchOrThrow();
            this.connectedViaP2P = true;
            executeOnClientThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ConnectionBridge1122.joinHost(rtcChannel, creds.userName, creds.profileId);
                    } catch (Throwable t) {
                        LOGGER.error("[bridge] joinHost failed", t);
                        rtcChannel.close();
                        try {
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc != null) {
                                mc.displayGuiScreen(null);
                            }
                        } catch (Throwable ignore) {}
                    }
                }
            });
        } catch (Throwable t) {
            LOGGER.error("[bridge] joinHost failed (no credentials)", t);
        }
    }

    @Override
    public void acceptGuest(RtcChannel rtcChannel, UUID guestProfileId) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) { rtcChannel.close(); return; }
            MinecraftServer server = mc.getIntegratedServer();
            if (server == null) { rtcChannel.close(); return; }
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        AcceptGuestBridge1122.acceptGuest(rtcChannel, guestProfileId);
                    } catch (Throwable t) {
                        LOGGER.error("[bridge] acceptGuest failed", t);
                        rtcChannel.close();
                    }
                }
            });
        } catch (Throwable t) {
            LOGGER.error("[bridge] acceptGuest dispatch failed", t);
            rtcChannel.close();
        }
    }

    @Override
    public void notifyToast(String type, String name, UUID profileId) {
        LOGGER.info("[toast] {} name={} profile={}", type, name, profileId);
    }

    @Override
    public void disconnectFromCurrentWorld() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                if (mc.world != null) {
                    mc.world.sendQuittingDisconnectingPacket();
                }
                mc.loadWorld(null);
            }
        } catch (Throwable t) {
            LOGGER.warn("[bridge] disconnectFromCurrentWorld failed", t);
        }
    }

    public static Snapshot tryFetch() {
        CredentialOverride o = override;
        if (o != null) return new Snapshot(o.profileId, o.userName, o.accessToken);
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return null;
            // 1.12.2 Session API
            // mc.getSession().getProfile() returns GameProfile
            // mc.getSession().getToken() returns access token
            com.mojang.authlib.GameProfile profile = mc.getSession().getProfile();
            if (profile == null) return null;
            UUID profileId = profile.getId();
            if (profileId == null) return null;
            String name = profile.getName();
            String token = mc.getSession().getToken();
            return new Snapshot(profileId, name, token);
        } catch (Throwable t) {
            LOGGER.debug("[bridge] credential fetch unavailable: {}", t.toString());
            return null;
        }
    }

    private static Snapshot tryFetchOrThrow() {
        Snapshot s = tryFetch();
        if (s == null) {
            throw new IllegalStateException("Minecraft client not available; supply -Dmsf.dev.* overrides in dev runs");
        }
        return s;
    }
}
