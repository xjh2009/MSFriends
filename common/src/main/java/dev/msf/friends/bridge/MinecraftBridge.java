package dev.msf.friends.bridge;

import com.mojang.authlib.yggdrasil.FriendsService;
import dev.msf.friends.p2p.P2PManager;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.PresenceHandler;
import dev.onvoid.webrtc.RTCPeerConnection;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Single dependency edge between this mod's pure-java backend and the live
 * Minecraft client. Every place that 26.2's source uses {@code Minecraft},
 * {@code IntegratedServer}, {@code Options}, or {@code playerSkinRenderCache}
 * goes through here.
 *
 * <p>The mod entry point installs an implementation that delegates to the
 * actual {@code net.minecraft.client.Minecraft} via reflection; tests can
 * install a stub that returns deterministic values.
 */
public interface MinecraftBridge {

    /** Current authenticated user's profile id (Mojang account UUID). */
    UUID profileId();

    /** Current authenticated user's account name. */
    String userName();

    /** Current Minecraft session access token. */
    String accessToken();

    /** Whether the underlying singleplayer/integrated server is publishing online (mirrors {@code IntegratedServer.isPublishedOnline()}). */
    boolean isHostingP2P();

    /** True while this client is connected as a P2P guest (not the host). */
    default boolean isConnectedViaP2P() { return false; }

    /** Sets the P2P-guest connection flag. Called by the bridge when joinHost() succeeds. */
    default void setConnectedViaP2P(boolean connected) {}

    /** Mirrors {@code Minecraft.friendsEnabled()} — true unless the user has disabled the entire feature. */
    default boolean friendsEnabled() { return true; }

    /** Mirrors {@code Minecraft.allowFriendRequests()}. */
    default boolean allowFriendRequests() { return true; }

    /** Whether in-game notification (toasts) is currently allowed; mirrors {@code Options.inGameNotification}. */
    default boolean inGameNotificationsEnabled() { return true; }

    /** Whether the player is currently in a level (so toasts may be suppressed depending on the option above). */
    default boolean inLevel() { return false; }

    /** Mirrors {@code Options.sharePresence()}. */
    default PresenceSharing presenceSharing() { return PresenceSharing.LIMITED; }

    /** Updates the current presence sharing mode. */
    default void setPresenceSharingMode(PresenceSharing mode) {}

    /** Whether the local player is currently hiding their presence from friends. */
    default boolean hiddenMode() { return false; }

    /** Updates the local hidden-mode flag. */
    default void setHiddenMode(boolean hidden) {}

    /** Returns the current multiplayer scope chosen by the user (OFF/LAN/ONLINE). */
    default MultiplayerScope multiplayerScope() { return MultiplayerScope.OFF; }

    /**
     * Sets the multiplayer scope. Called from the web UI when the user explicitly
     * chooses OFF, LAN, or ONLINE mode — mirroring 26.2's MultiplayerOptionsScreen.
     */
    default void setMultiplayerScope(MultiplayerScope scope) {}

    /** Run the runnable on the client main thread (mirrors {@code Minecraft.execute(Runnable)}). */
    void executeOnClientThread(Runnable r);

    /** Toast/UI sink. The reference implementation just forwards to logging. */
    default void notifyToast(String type, String name, @Nullable UUID profileId) {}

    /** Disconnect any current world / pending connection (mirrors {@code Minecraft.disconnectWithProgressScreen(false)}). */
    default void disconnectFromCurrentWorld() {}

    /** Hand off a freshly-completed Netty channel to the client as a server connection.
     *  Mirrors the body of {@code RtcHandshakeHandler.joinHost}. */
    default void joinHost(io.netty.channel.Channel rtcChannel, RTCPeerConnection peerConnection) {
        throw new UnsupportedOperationException("Not implemented in this build (no game-side wire-up yet).");
    }

    /** Hand off a freshly-completed Netty channel to the integrated server.
     *  Mirrors {@code RtcHandshakeHandler.acceptGuest}. */
    default void acceptGuest(io.netty.channel.Channel rtcChannel, UUID guestProfileId) {
        throw new UnsupportedOperationException("Not implemented in this build (no game-side wire-up yet).");
    }

    /** Visible only to the mod entry point — wires things back. */
    default void registerBackend(FriendsService friendsService,
                                 PlayerSocialManager social,
                                 PresenceHandler presence,
                                 P2PManager p2p) {}

    enum PresenceSharing { NONE, LIMITED, ALL }
    enum MultiplayerScope { OFF, LAN, ONLINE }
}
