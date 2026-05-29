package dev.msf.friends.social;

import dev.msf.friends.authlib.FriendsService;
import dev.msf.friends.authlib.response.FriendDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Java 8 port of PlayerSocialManager.
 */
public final class PlayerSocialManager {
    private static final Logger LOGGER = Logging.get();

    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final RemoteFriendListUpdateHandler remoteFriendListUpdateHandler;
    private final PresenceHandler presenceHandler;
    private final Set<UUID> hiddenPlayers = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<UUID, Boolean>());
    private final Map<String, UUID> discoveredNamesToUUID = new HashMap<>();
    private volatile boolean onlineMode;
    private volatile boolean friendListEnabled;
    private volatile boolean allowFriendRequests;

    public PlayerSocialManager(MinecraftBridge bridge,
                               FriendsService friendsService,
                               RemoteFriendListUpdateHandler updater) {
        this.bridge = bridge;
        this.friendsService = friendsService;
        this.remoteFriendListUpdateHandler = updater;
        this.friendListEnabled = bridge.friendsEnabled();
        this.allowFriendRequests = bridge.allowFriendRequests();
        this.presenceHandler = new PresenceHandler(bridge, friendsService, new Supplier<PlayerSocialManager>() {
            @Override public PlayerSocialManager get() { return PlayerSocialManager.this; }
        });
    }

    public RemoteFriendListUpdateHandler getRemoteFriendListUpdateHandler() { return remoteFriendListUpdateHandler; }
    public PresenceHandler getPresenceHandler() { return presenceHandler; }
    public FriendsService getFriendsService() { return friendsService; }

    public void addFriendListUpdateListener(Runnable r)    { remoteFriendListUpdateHandler.addUpdateListener(r); }
    public void removeFriendListUpdateListener(Runnable r) { remoteFriendListUpdateHandler.removeUpdateListener(r); }

    // -------- queries --------

    public List<PlayerData> getFriends() {
        return remap(remoteFriendListUpdateHandler.getLatestFriendData().friends());
    }

    public List<PlayerData> getIncomingRequests() {
        return remap(remoteFriendListUpdateHandler.getLatestFriendData().incomingRequests());
    }

    public List<PlayerData> getOutgoingRequests() {
        return remap(remoteFriendListUpdateHandler.getLatestFriendData().outgoingRequests());
    }

    public RemoteFriendListUpdateHandler.State getFriendListState() { return remoteFriendListUpdateHandler.getState(); }

    public boolean isFriendsPmid(UUID pmid) {
        if (pmid == null) return false;
        UUID profileId = presenceHandler.getProfileIdFromPmid(pmid);
        if (profileId == null) return false;
        for (PlayerData p : getFriends()) {
            if (p.id().equals(profileId)) return true;
        }
        return false;
    }

    public boolean isFriend(UUID profileId) {
        for (PlayerData p : getFriends()) {
            if (p.id().equals(profileId)) return true;
        }
        return false;
    }

    // -------- block list --------

    public void hidePlayer(UUID id) { hiddenPlayers.add(id); }
    public void showPlayer(UUID id) { hiddenPlayers.remove(id); }
    public boolean shouldHideMessageFrom(UUID id) { return isHidden(id); }
    public boolean isHidden(UUID id) { return hiddenPlayers.contains(id); }
    public boolean isBlocked(UUID id) { return false; }
    public Set<UUID> getHiddenPlayers() { return hiddenPlayers; }

    public UUID getDiscoveredUUID(String name) {
        return discoveredNamesToUUID.getOrDefault(name, new UUID(0L, 0L));
    }

    public void startOnlineMode() {
        this.onlineMode = true;
        if (friendListEnabled) remoteFriendListUpdateHandler.start();
    }

    public void stopOnlineMode() {
        this.onlineMode = false;
        remoteFriendListUpdateHandler.stop();
    }

    public boolean isOnlineMode() { return onlineMode; }

    // -------- friend actions --------

    public CompletableFuture<FriendsService.ResultCode> sendFriendRequest(String name) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.sendFriendRequest(name); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> sendFriendRequest(UUID id) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.sendFriendRequest(id); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> removeFriend(UUID id) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.removeFriend(id); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> acceptIncomingFriendRequest(UUID id) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.acceptIncomingFriendRequest(id); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> declineIncomingFriendRequest(UUID id) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.declineIncomingFriendRequest(id); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> revokeOutgoingFriendRequest(UUID id) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.revokeOutgoingFriendRequest(id); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> updateFriendSettings(final boolean fle, final boolean afr) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() {
                FriendsService.ResultCode r = friendsService.updateFriendSettings(fle, afr);
                if (r == FriendsService.ResultCode.SUCCESS) {
                    friendListEnabled = fle;
                    allowFriendRequests = afr;
                }
                return r;
            }
        });
    }

    private CompletableFuture<FriendsService.ResultCode> runAction(final Supplier<FriendsService.ResultCode> action) {
        return CompletableFuture.supplyAsync(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() {
                FriendsService.ResultCode r = action.get();
                handleResult(r);
                return r;
            }
        }).thenCompose(new java.util.function.Function<FriendsService.ResultCode, CompletableFuture<FriendsService.ResultCode>>() {
            @Override public CompletableFuture<FriendsService.ResultCode> apply(FriendsService.ResultCode r) {
                if (r == FriendsService.ResultCode.SUCCESS) {
                    return remoteFriendListUpdateHandler.forceUpdate().thenApply(
                            new java.util.function.Function<Void, FriendsService.ResultCode>() {
                                @Override public FriendsService.ResultCode apply(Void u) { return r; }
                            });
                }
                return CompletableFuture.completedFuture(r);
            }
        });
    }

    private void handleResult(FriendsService.ResultCode r) {
        if (r != FriendsService.ResultCode.SUCCESS) showFailureToast(r);
    }

    private void showFailureToast(FriendsService.ResultCode r) {
        String key;
        switch (r) {
            case TOO_MANY_REQUESTS:     key = "friend.action.rate_limited"; break;
            case UNKNOWN_PROFILE:       key = "friend.action.unknown_profile"; break;
            case FORBIDDEN:             key = "friend.action.forbidden"; break;
            case SERVICE_NOT_AVAILABLE: key = "friend.action.unavailable"; break;
            case ERROR:                 key = "friend.action.failed"; break;
            default: key = null; break;
        }
        if (key == null) return;
        final String toastKey = key;
        bridge.executeOnClientThread(new Runnable() {
            @Override public void run() { bridge.notifyToast(toastKey, "", null); }
        });
    }

    // -------- toggles --------

    public boolean isFriendListEnabled() { return friendListEnabled; }
    public void setFriendListEnabled(boolean v) {
        this.friendListEnabled = v;
        if (v) remoteFriendListUpdateHandler.start();
        else   remoteFriendListUpdateHandler.stop();
    }
    public boolean isAllowFriendRequests() { return allowFriendRequests; }
    public void setAllowFriendRequests(boolean v) { this.allowFriendRequests = v; }

    private static List<PlayerData> remap(List<FriendDto> list) {
        List<PlayerData> result = new ArrayList<>();
        for (FriendDto f : list) {
            result.add(new PlayerData(f.profileId(), f.name()));
        }
        return result;
    }

    public static final class PlayerData {
        private final UUID id;
        private final String name;
        public PlayerData(UUID id, String name) { this.id = id; this.name = name; }
        public UUID id() { return id; }
        public String name() { return name; }
    }
}
