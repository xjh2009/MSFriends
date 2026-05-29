package dev.msf.friends.bridge;

import java.util.UUID;

/**
 * Simplified MinecraftBridge interface for MC 1.11.2 (Java 8).
 * Mirrors the common/ MinecraftBridge but without Java 17+ features.
 */
public interface MinecraftBridge1112 {

    UUID profileId();
    String userName();
    String accessToken();
    boolean isHostingP2P();
    boolean isConnectedViaP2P();
    void setConnectedViaP2P(boolean connected);
    boolean friendsEnabled();
    boolean allowFriendRequests();
    boolean inGameNotificationsEnabled();
    boolean inLevel();
    PresenceSharing presenceSharing();
    void setPresenceSharingMode(PresenceSharing mode);
    boolean hiddenMode();
    void setHiddenMode(boolean hidden);
    MultiplayerScope multiplayerScope();
    void setMultiplayerScope(MultiplayerScope scope);
    void executeOnClientThread(Runnable r);
    void notifyToast(String type, String name, UUID profileId);
    void disconnectFromCurrentWorld();

    enum PresenceSharing { NONE, LIMITED, ALL }
    enum MultiplayerScope { OFF, LAN, ONLINE }
}
