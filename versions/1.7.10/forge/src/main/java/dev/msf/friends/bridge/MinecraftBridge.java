package dev.msf.friends.bridge;

import java.util.UUID;

/**
 * Core interface bridging the mod logic to MC 1.7.10 client.
 */
public interface MinecraftBridge {

    UUID profileId();
    String userName();
    String accessToken();

    boolean isHostingP2P();
    boolean inLevel();

    enum PresenceSharing { NONE, LIMITED, ALL }
    enum MultiplayerScope { OFF, LAN, ONLINE }

    PresenceSharing presenceSharing();
    void setPresenceSharing(PresenceSharing sharing);

    MultiplayerScope multiplayerScope();
    void setMultiplayerScope(MultiplayerScope scope);

    boolean hiddenMode();
    void setHiddenMode(boolean hidden);

    boolean inGameNotificationsEnabled();
    void setInGameNotificationsEnabled(boolean enabled);

    boolean friendsEnabled();
    boolean allowFriendRequests();

    /** Get host address for presence (returns null if not hosting) */
    String joinHost();

    /** Join a host via P2P (initiator/guest side) */
    void joinHost(UUID peerPmid, io.netty.channel.Channel rtcChannel);

    /** Accept a guest connection (host side) */
    void acceptGuest(UUID guestProfileId, io.netty.channel.Channel rtcChannel);

    /** Execute a task on the main client thread */
    void executeOnClientThread(Runnable task);

    /** Show a toast notification */
    void notifyToast(String toastType, String playerName, UUID profileId);
}
