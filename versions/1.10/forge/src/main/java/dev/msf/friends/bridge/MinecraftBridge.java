package dev.msf.friends.bridge;

import io.netty.channel.Channel;
import java.util.UUID;

/**
 * Java 8 compatible version of the bridge interface.
 * Mirrors the root :common MinecraftBridge but avoids records/sealed types.
 *
 * <p>Each Minecraft version + loader combination provides an implementation
 * that delegates to the actual game API via reflection.
 */
public interface MinecraftBridge {

    UUID profileId();
    String userName();
    String accessToken();

    boolean isHostingP2P();
    default boolean isConnectedViaP2P() { return false; }
    default void setConnectedViaP2P(boolean connected) {}
    default boolean friendsEnabled() { return true; }
    default boolean allowFriendRequests() { return true; }
    default boolean inGameNotificationsEnabled() { return true; }
    default boolean inLevel() { return false; }
    default PresenceSharing presenceSharing() { return PresenceSharing.LIMITED; }
    default void setPresenceSharingMode(PresenceSharing mode) {}
    default boolean hiddenMode() { return false; }
    default void setHiddenMode(boolean hidden) {}
    default MultiplayerScope multiplayerScope() { return MultiplayerScope.OFF; }
    default void setMultiplayerScope(MultiplayerScope scope) {}

    void executeOnClientThread(Runnable r);

    default void notifyToast(String type, String name, UUID profileId) {}
    default void disconnectFromCurrentWorld() {}
    default void joinHost(Channel rtcChannel) {
        throw new UnsupportedOperationException("Not implemented in this build.");
    }
    default void acceptGuest(Channel rtcChannel, UUID guestProfileId) {
        throw new UnsupportedOperationException("Not implemented in this build.");
    }

    enum PresenceSharing { NONE, LIMITED, ALL }
    enum MultiplayerScope { OFF, LAN, ONLINE }
}
