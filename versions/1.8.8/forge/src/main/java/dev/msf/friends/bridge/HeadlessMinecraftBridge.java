package dev.msf.friends.bridge;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.screen.ToastBridge;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Session;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * {@link MinecraftBridge} implementation for MC 1.8.8 (MCP mappings).
 *
 * <p>Key MCP 1.8.8 differences from Yarn 1.19.2:
 * <ul>
 *   <li>{@code Minecraft.getMinecraft()} (static) instead of {@code MinecraftClient.getInstance()}</li>
 *   <li>{@code mc.theWorld} instead of {@code mc.world}</li>
 *   <li>{@code mc.getIntegratedServer()} instead of {@code mc.getServer()}</li>
 *   <li>{@code mc.addScheduledTask(r)} instead of {@code mc.execute(r)}</li>
 *   <li>{@code mc.displayGuiScreen(s)} instead of {@code mc.setScreen(s)}</li>
 *   <li>{@code Session.getToken()} instead of {@code Session.getAccessToken()}</li>
 *   <li>{@code Session.getPlayerID()} returns String (UUID without dashes)</li>
 * </ul>
 */
public final class HeadlessMinecraftBridge implements MinecraftBridge {
    private static final Logger LOGGER = Logging.get();

    /** Credential override for dev/offline use. */
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

    /** Snapshot of credentials fetched from the MC session. */
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

    @Override public boolean inLevel() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.theWorld != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public void executeOnClientThread(Runnable r) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.addScheduledTask(r);
                return;
            }
        } catch (Throwable ignore) {}
        mainThread.execute(new Runnable() {
            @Override public void run() {
                try { r.run(); }
                catch (Throwable t) { LOGGER.warn("[bridge] main-thread runnable threw", t); }
            }
        });
    }

    @Override
    public void joinHost(io.netty.channel.Channel rtcChannel, dev.onvoid.webrtc.RTCPeerConnection peerConnection) {
        try {
            final Snapshot creds = tryFetchOrThrow();
            this.connectedViaP2P = true;
            executeOnClientThread(new Runnable() {
                @Override public void run() {
                    try {
                        ConnectionBridge.joinHost(rtcChannel, creds.userName, creds.profileId);
                    } catch (Throwable t) {
                        LOGGER.error("[bridge] joinHost failed", t);
                        dev.msf.friends.webrtc.RtcChannel.dispose(peerConnection, null);
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
    public void acceptGuest(io.netty.channel.Channel rtcChannel, UUID guestProfileId) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) { rtcChannel.close(); return; }
            net.minecraft.server.MinecraftServer server = mc.getIntegratedServer();
            if (server == null) { rtcChannel.close(); return; }
            server.addScheduledTask(new Runnable() {
                @Override public void run() {
                    try {
                        AcceptGuestBridge.acceptGuest(rtcChannel, guestProfileId);
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

    @Override public void notifyToast(String type, String name, @Nullable UUID profileId) {
        LOGGER.info("[toast] {} name={} profile={}", type, name, profileId);
        executeOnClientThread(new Runnable() {
            @Override public void run() {
                ToastBridge.showToast(type, name, profileId);
            }
        });
    }

    @Override public void disconnectFromCurrentWorld() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.theWorld != null) {
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
            }
        } catch (Throwable t) {
            LOGGER.warn("[bridge] disconnectFromCurrentWorld failed", t);
        }
    }

    public static Optional<Snapshot> tryFetch() {
        CredentialOverride o = override;
        if (o != null) return Optional.of(new Snapshot(o.profileId, o.userName, o.accessToken));
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
                LOGGER.trace("[bridge] Minecraft.getMinecraft() returned null");
                return Optional.empty();
            }
            Session session = mc.getSession();
            if (session == null) {
                LOGGER.trace("[bridge] getSession() returned null");
                return Optional.empty();
            }
            String token = session.getToken();
            String name = session.getUsername();
            // 1.8.8 MCP: getPlayerID() returns UUID string without dashes
            String uuidStr = session.getPlayerID();
            if (uuidStr == null || uuidStr.isEmpty()) return Optional.empty();
            // Normalize: add dashes if missing
            if (uuidStr.length() == 32) {
                uuidStr = uuidStr.substring(0, 8) + "-" + uuidStr.substring(8, 12) + "-"
                        + uuidStr.substring(12, 16) + "-" + uuidStr.substring(16, 20) + "-"
                        + uuidStr.substring(20);
            }
            UUID profileId = UUID.fromString(uuidStr);
            return Optional.of(new Snapshot(profileId, name, token));
        } catch (Throwable t) {
            LOGGER.debug("[bridge] direct API fetch unavailable: {}", t.toString());
            return Optional.empty();
        }
    }

    private static Snapshot tryFetchOrThrow() {
        Optional<Snapshot> snap = tryFetch();
        if (snap.isPresent()) return snap.get();
        throw new IllegalStateException("Minecraft client not available; supply -Dmsf.dev.* overrides in dev runs");
    }
}
