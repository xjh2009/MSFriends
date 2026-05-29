package dev.msf.friends.bridge;

import dev.msf.friends.MsfFriendsConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Reflection-based MinecraftBridge for Forge 1.8.9.
 * Java 8 compatible. Uses MC 1.8.9 API: Minecraft.getMinecraft(), Session, etc.
 */
public final class HeadlessMinecraftBridge implements MinecraftBridge {

    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;
    private static final CountDownLatch CREDENTIALS_LATCH = new CountDownLatch(1);

    private static volatile CredentialOverride credentialOverride;

    // Snapshot class — plain Java 8 class (not a record)
    public static class Snapshot {
        public final String playerName;
        public final String uuid;

        public Snapshot(String playerName, String uuid) {
            this.playerName = playerName;
            this.uuid = uuid;
        }
    }

    // CredentialOverride — plain Java 8 class (not a record)
    public static class CredentialOverride {
        public final String playerName;
        public final String uuid;

        public CredentialOverride(String playerName, String uuid) {
            this.playerName = playerName;
            this.uuid = uuid;
        }
    }

    @Override
    public String getPlayerName() {
        Snapshot s = tryFetch();
        return s != null ? s.playerName : null;
    }

    @Override
    public String getPlayerUUID() {
        Snapshot s = tryFetch();
        return s != null ? s.uuid : null;
    }

    @Override
    public boolean isIntegratedServerRunning() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.theWorld != null && mc.theWorld.isRemote == false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isConnectedToServer() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.theWorld != null && mc.theWorld.isRemote;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getServerAddress() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getCurrentServerData() != null) {
                return mc.getCurrentServerData().serverIP;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    public void showNotification(String message) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                ChatComponentText text = new ChatComponentText(
                        EnumChatFormatting.GREEN + "[MSF Friends] " + EnumChatFormatting.RESET + message);
                mc.thePlayer.addChatMessage(text);
            }
        } catch (Exception e) {
            LOGGER.debug("[bridge] Could not show notification: {}", e.getMessage());
        }
    }

    /**
     * Try to fetch credentials from the current MC session.
     */
    public static Snapshot tryFetch() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.getSession() == null) return null;

            String name = mc.getSession().getUsername();
            String uuid = mc.getSession().getPlayerID();

            if (name != null && uuid != null) {
                CREDENTIALS_LATCH.countDown();
                return new Snapshot(name, uuid);
            }
        } catch (Exception e) {
            LOGGER.debug("[bridge] tryFetch failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Block until credentials are available or timeout.
     */
    public static void waitForCredentials(long timeoutMs) throws InterruptedException {
        CREDENTIALS_LATCH.await(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
