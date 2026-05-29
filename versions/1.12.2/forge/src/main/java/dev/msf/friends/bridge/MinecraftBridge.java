package dev.msf.friends.bridge;

import dev.msf.friends.webrtc.RtcChannel;
import dev.onvoid.webrtc.RTCPeerConnection;

import java.util.UUID;

/**
 * Core bridge interface (Java 8 compatible). Same semantics as 1.19.2 version.
 */
public interface MinecraftBridge {

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

    void joinHost(RtcChannel rtcChannel, RTCPeerConnection peerConnection);
    void acceptGuest(RtcChannel rtcChannel, UUID guestProfileId);

    enum PresenceSharing { NONE, LIMITED, ALL }
    enum MultiplayerScope { OFF, LAN, ONLINE }
}
