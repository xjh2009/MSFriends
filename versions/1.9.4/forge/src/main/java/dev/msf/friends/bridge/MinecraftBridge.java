package dev.msf.friends.bridge;

/**
 * Abstraction over Minecraft-specific operations.
 * Java 8 compatible.
 */
public interface MinecraftBridge {

    enum PresenceSharing { EVERYONE, FRIENDS_ONLY, NOBODY }
    enum MultiplayerScope { EVERYONE, FRIENDS_ONLY, NOBODY }

    /** Player name or null if not available. */
    String getPlayerName();

    /** Player UUID as string, or null. */
    String getPlayerUUID();

    /** True if the integrated (LAN) server is running. */
    boolean isIntegratedServerRunning();

    /** True if the client is currently connected to a server. */
    boolean isConnectedToServer();

    /** Get the current server address, or null. */
    String getServerAddress();

    /** Show a notification to the player. */
    void showNotification(String message);
}
