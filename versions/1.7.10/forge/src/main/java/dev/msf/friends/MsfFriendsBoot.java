package dev.msf.friends;

import com.mojang.authlib.yggdrasil.FriendsService;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.P2PManager;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.PresenceHandler;
import dev.msf.friends.social.RemoteFriendListUpdateHandler;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Bootstrap that wires the social layer, P2P manager and presence handler together.
 * Called from MsfFriendsForge after MinecraftBridge is ready.
 */
public final class MsfFriendsBoot {
    private static final Logger LOGGER = Logging.get(MsfFriendsBoot.class);

    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final PlayerSocialManager socialManager;
    private final RemoteFriendListUpdateHandler friendListUpdater;
    private final P2PManager p2pManager;
    private final PresenceHandler presenceHandler;
    private boolean started;

    public MsfFriendsBoot(MinecraftBridge bridge, FriendsService friendsService) {
        this.bridge = bridge;
        this.friendsService = friendsService;
        this.friendListUpdater = new RemoteFriendListUpdateHandler(friendsService, bridge);
        this.socialManager = new PlayerSocialManager(bridge, friendsService, friendListUpdater);
        this.p2pManager = new P2PManager(bridge,
                SignalingServiceClientCredentials.fromBridge(bridge),
                () -> socialManager);
        this.presenceHandler = socialManager.getPresenceHandler();
    }

    public MinecraftBridge bridge() { return bridge; }
    public FriendsService friendsService() { return friendsService; }
    public PlayerSocialManager socialManager() { return socialManager; }
    public RemoteFriendListUpdateHandler friendListUpdater() { return friendListUpdater; }
    public P2PManager p2pManager() { return p2pManager; }
    public PresenceHandler presenceHandler() { return presenceHandler; }

    public synchronized void start() {
        if (started) return;
        started = true;
        LOGGER.info("[MSF] Starting MSF Friends");
        friendListUpdater.start();
        LOGGER.info("[MSF] MSF Friends started");
    }

    public synchronized void shutdown() {
        if (!started) return;
        started = false;
        LOGGER.info("[MSF] Shutting down MSF Friends");
        friendListUpdater.stop();
        p2pManager.shutdown();
        LOGGER.info("[MSF] MSF Friends shut down");
    }

    private static final class SignalingServiceClientCredentials implements dev.msf.friends.p2p.client.SignalingServiceClient.UserCredentials {
        private final String accessToken;
        private final UUID profileId;
        private final String userName;

        private SignalingServiceClientCredentials(String accessToken, UUID profileId, String userName) {
            this.accessToken = accessToken;
            this.profileId = profileId;
            this.userName = userName;
        }

        static SignalingServiceClientCredentials fromBridge(MinecraftBridge bridge) {
            return new SignalingServiceClientCredentials(bridge.accessToken(), bridge.profileId(), bridge.userName());
        }

        @Override public String accessToken() { return accessToken; }
        @Override public UUID profileId() { return profileId; }
        @Override public String userName() { return userName; }
    }
}
