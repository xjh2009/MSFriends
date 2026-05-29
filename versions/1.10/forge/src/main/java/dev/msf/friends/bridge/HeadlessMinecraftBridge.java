package dev.msf.friends.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Forge 1.10 bridge implementation using direct MCP deobfuscated API.
 *
 * <p>Forge 1.10.2 ships with MCP deobfuscated names so we can call
 * {@code Minecraft.getMinecraft()} and {@code Session} directly
 * — no reflection needed for vanilla classes.
 */
public final class HeadlessMinecraftBridge implements MinecraftBridge {
    private static final Logger LOGGER = LogManager.getLogger("MSF/Friends");

    /** Dev-mode credential override (set via -Dmsf.dev.*). */
    public static CredentialOverride override;

    private volatile boolean connectedViaP2P;
    private volatile boolean hostingP2P;

    public static class CredentialOverride {
        public final UUID uuid;
        public final String token;
        public final String userName;

        public CredentialOverride(UUID uuid, String token, String userName) {
            this.uuid = uuid;
            this.token = token;
            this.userName = userName;
        }
    }

    @Override
    public UUID profileId() {
        if (override != null) return override.uuid;
        Session session = Minecraft.getMinecraft().getSession();
        return session.getProfile().getId();
    }

    @Override
    public String userName() {
        if (override != null) return override.userName;
        Session session = Minecraft.getMinecraft().getSession();
        return session.getUsername();
    }

    @Override
    public String accessToken() {
        if (override != null) return override.token;
        Session session = Minecraft.getMinecraft().getSession();
        return session.getToken();
    }

    @Override
    public boolean isHostingP2P() {
        return hostingP2P;
    }

    public void setHostingP2P(boolean hosting) {
        this.hostingP2P = hosting;
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
    public boolean inLevel() {
        return Minecraft.getMinecraft().world != null;
    }

    @Override
    public void executeOnClientThread(Runnable r) {
        Minecraft.getMinecraft().addScheduledTask(r);
    }

    @Override
    public void disconnectFromCurrentWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.world != null) {
                mc.world.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
            }
        });
    }

    /**
     * Try to fetch credentials from the Minecraft session via reflection.
     * Returns true if all three fields (uuid, token, username) were obtained.
     */
    public static boolean tryFetch(java.util.Map<String, String> out) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            Session session = mc.getSession();
            if (session == null) return false;
            UUID uuid = session.getProfile().getId();
            String token = session.getToken();
            String username = session.getUsername();
            if (uuid == null || token == null || username == null) return false;
            out.put("uuid", uuid.toString());
            out.put("token", token);
            out.put("username", username);
            return true;
        } catch (Exception e) {
            LOGGER.debug("[bridge] tryFetch failed", e);
            return false;
        }
    }
}
