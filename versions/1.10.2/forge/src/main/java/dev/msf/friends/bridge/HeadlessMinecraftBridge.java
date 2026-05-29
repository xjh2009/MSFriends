package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import dev.onvoid.webrtc.RTCPeerConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * MinecraftBridge implementation for MC 1.10.2 (MCP stable_29 mappings).
 */
public class HeadlessMinecraftBridge implements MinecraftBridge {
    private static final Logger LOGGER = Logging.get();

    private final Executor mainThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "msf-mc-bridge");
        t.setDaemon(true);
        return t;
    });

    private volatile MultiplayerScope multiplayerScopeMode = MultiplayerScope.OFF;
    private volatile PresenceSharing presenceSharingMode = PresenceSharing.ALL;
    private volatile boolean connectedViaP2P = false;
    private volatile boolean hiddenMode = false;

    @Override
    public UUID profileId() {
        String id = getMinecraft().getSession().getPlayerID();
        if (!id.contains("-")) {
            id = id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20);
        }
        return UUID.fromString(id);
    }

    @Override
    public String userName() {
        return getMinecraft().getSession().getUsername();
    }

    @Override
    public String accessToken() {
        return getMinecraft().getSession().getToken();
    }

    @Override
    public boolean isHostingP2P() {
        Minecraft mc = getMinecraft();
        return mc.getIntegratedServer() != null && mc.getIntegratedServer().getPublic();
    }

    @Override
    public boolean isConnectedViaP2P() {
        return connectedViaP2P;
    }

    @Override
    public void setConnectedViaP2P(boolean connected) {
        this.connectedViaP2P = connected;
    }

    @Override
    public boolean friendsEnabled() {
        return true;
    }

    @Override
    public boolean allowFriendRequests() {
        return true;
    }

    @Override
    public MultiplayerScope multiplayerScope() {
        return multiplayerScopeMode;
    }

    @Override
    public void setMultiplayerScope(MultiplayerScope scope) {
        LOGGER.info("[bridge] multiplayer scope changed: {} -> {}", multiplayerScopeMode, scope);
        this.multiplayerScopeMode = scope;
    }

    @Override
    public void executeOnClientThread(Runnable r) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.addScheduledTask(r);
        } else {
            mainThread.execute(() -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    LOGGER.warn("[bridge] main-thread runnable threw", t);
                }
            });
        }
    }

    @Override
    public boolean inLevel() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.world != null && mc.player != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void disconnectFromCurrentWorld() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.loadWorld(null);
            }
        } catch (Throwable t) {
            LOGGER.warn("[bridge] disconnectFromCurrentWorld failed", t);
        }
    }

    @Override
    public PresenceSharing presenceSharing() {
        return presenceSharingMode;
    }

    @Override
    public void setPresenceSharing(PresenceSharing sharing) {
        this.presenceSharingMode = sharing;
    }

    @Override
    public boolean hiddenMode() {
        return hiddenMode;
    }

    @Override
    public void setHiddenMode(boolean hidden) {
        this.hiddenMode = hidden;
    }

    @Override
    public boolean inGameNotificationsEnabled() {
        return true;
    }

    @Override
    public void notifyToast(String toastType, String playerName, UUID profileId) {
        LOGGER.info("[bridge][toast] {} player={} profile={}", toastType, playerName, profileId);
        // In headless mode, just log; the web UI will consume these via the HTTP server
    }

    @Override
    public void joinHost(RtcChannel channel, RTCPeerConnection peerConnection) {
        LOGGER.info("[bridge] joinHost called — connecting to remote host via P2P");
        try {
            // In 1.10.2, we would create a Connection from the channel and trigger
            // the client-side login sequence. For now, this is a placeholder that
            // the Forge-specific implementation would fill in.
            //
            // The general flow is:
            // 1. Create a Connection object wrapping the RtcChannel
            // 2. Set up NetHandlerPlayClient
            // 3. Call mc.loadWorld(null) to disconnect current world
            // 4. Set the new connection as active
            //
            // This requires deep MC internals access. We'll implement this after
            // verifying basic P2P handshake works.
            LOGGER.warn("[bridge] joinHost: P2P host connection not yet fully implemented for 1.10.2");
            channel.close();
        } catch (Throwable t) {
            LOGGER.error("[bridge] joinHost failed", t);
            channel.close();
        }
    }

    @Override
    public void acceptGuest(RtcChannel channel, UUID guestProfileId) {
        LOGGER.info("[bridge] acceptGuest called — accepting P2P guest profile={}", guestProfileId);
        try {
            // Mark this as secure transport (identity already verified via signaling)
            channel.attr(RtcChannel.SECURE_TRANSPORT).set(Boolean.TRUE);
            channel.attr(RtcChannel.INTENDED_PROFILE_ID).set(guestProfileId);
            // In 1.10.2, the integrated server would accept this connection.
            // The flow is:
            // 1. Create a Connection wrapping the RtcChannel
            // 2. Register with the integrated server's network system
            // 3. Trigger server-side login
            //
            // This requires deep MC internals access. Placeholder for now.
            LOGGER.warn("[bridge] acceptGuest: P2P guest acceptance not yet fully implemented for 1.10.2");
            channel.close();
        } catch (Throwable t) {
            LOGGER.error("[bridge] acceptGuest failed", t);
            channel.close();
        }
    }

    private Minecraft getMinecraft() {
        return Minecraft.getMinecraft();
    }
}
