package dev.msf.friends.bridge;

import dev.msf.friends.webrtc.RtcChannel;
import dev.onvoid.webrtc.RTCPeerConnection;

import java.util.UUID;

/**
 * Single dependency edge between this mod's pure-java backend and the live
 * Minecraft client. Adapted for Java 8 / MC 1.10.2.
 */
public interface MinecraftBridge {

    /** Current authenticated user's profile id (Mojang account UUID). */
    UUID profileId();

    /** Current authenticated user's account name. */
    String userName();

    /** Current Minecraft session access token. */
    String accessToken();

    /** Whether the underlying singleplayer/integrated server is publishing online. */
    boolean isHostingP2P();

    /** True while this client is connected as a P2P guest. */
    boolean isConnectedViaP2P();

    /** Sets the P2P-guest connection flag. */
    void setConnectedViaP2P(boolean connected);

    /** Whether friends feature is enabled. */
    boolean friendsEnabled();

    /** Whether friend requests are allowed. */
    boolean allowFriendRequests();

    /** Returns the current multiplayer scope. */
    MultiplayerScope multiplayerScope();

    /** Sets the multiplayer scope. */
    void setMultiplayerScope(MultiplayerScope scope);

    /** Run the runnable on the client main thread. */
    void executeOnClientThread(Runnable r);

    /** Whether the player is currently in a level. */
    boolean inLevel();

    /** Disconnect any current world / pending connection. */
    void disconnectFromCurrentWorld();

    // ---- Presence / P2P extensions ----

    /** Returns the current presence sharing mode. */
    PresenceSharing presenceSharing();

    /** Sets the presence sharing mode. */
    void setPresenceSharing(PresenceSharing sharing);

    /** Whether hidden mode (appear offline) is active. */
    boolean hiddenMode();

    /** Sets hidden mode. */
    void setHiddenMode(boolean hidden);

    /** Whether in-game notifications are enabled. */
    boolean inGameNotificationsEnabled();

    /** Show a toast notification. toastType is an i18n key, playerName is context, profileId is optional. */
    void notifyToast(String toastType, String playerName, UUID profileId);

    /**
     * Join a remote host via P2P.
     * Called by RtcHandshakeHandler when the initiator (guest) completes the handshake.
     */
    void joinHost(RtcChannel channel, RTCPeerConnection peerConnection);

    /**
     * Accept a P2P guest.
     * Called by RtcHandshakeHandler when the responder (host) completes the handshake.
     */
    void acceptGuest(RtcChannel channel, UUID guestProfileId);

    enum PresenceSharing { NONE, LIMITED, ALL }
    enum MultiplayerScope { OFF, LAN, ONLINE }
}
