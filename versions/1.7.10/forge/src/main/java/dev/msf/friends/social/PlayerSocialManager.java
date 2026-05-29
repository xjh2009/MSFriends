package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Friend list management (1.7.10 / Java 8 port).
 */
public final class PlayerSocialManager {
    private static final Logger LOGGER = Logging.get(PlayerSocialManager.class);

    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final RemoteFriendListUpdateHandler remoteFriendListUpdateHandler;
    private final PresenceHandler presenceHandler;
    private final Set<UUID> hiddenPlayers = new HashSet<UUID>();
    private final Map<String, UUID> discoveredNamesToUUID = new HashMap<String, UUID>();
    private boolean onlineMode;
    private boolean friendListEnabled;
    private boolean allowFriendRequests;

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

    // -------- lifecycle --------

    public void startOnlineMode() {
        this.onlineMode = true;
        if (friendListEnabled) remoteFriendListUpdateHandler.start();
    }

    public void stopOnlineMode() {
        this.onlineMode = false;
        remoteFriendListUpdateHandler.stop();
    }

    public boolean isOnlineMode() { return onlineMode; }

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

    public RemoteFriendListUpdateHandler.State getFriendListState() {
        return remoteFriendListUpdateHandler.getState();
    }

    public boolean isFriendsPmid(UUID pmid) {
        if (pmid == null) return false;
        UUID profileId = presenceHandler.getProfileIdFromPmid(pmid);
        return profileId != null && isFriend(profileId);
    }

    public boolean isFriend(UUID profileId) {
        for (PlayerData p : getFriends()) {
            if (p.id().equals(profileId)) return true;
        }
        return false;
    }

    // -------- actions --------

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

    public CompletableFuture<FriendsService.ResultCode> blockPlayer(UUID id) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.blockPlayer(id); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> acceptFriendRequest(UUID id) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.acceptFriendRequest(id); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> declineFriendRequest(UUID id) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.declineFriendRequest(id); }
        });
    }

    public CompletableFuture<FriendsService.ResultCode> updateFriendSettings(boolean fle, boolean afr) {
        return runAction(new Supplier<FriendsService.ResultCode>() {
            @Override public FriendsService.ResultCode get() { return friendsService.updateFriendSettings(fle, afr); }
        });
    }

    private CompletableFuture<FriendsService.ResultCode> runAction(final Supplier<FriendsService.ResultCode> action) {
        final CompletableFuture<FriendsService.ResultCode> f = new CompletableFuture<FriendsService.ResultCode>();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    FriendsService.ResultCode r = action.get();
                    handleResult(r);
                    f.complete(r);
                } catch (Exception e) {
                    LOGGER.error("Friend action failed", e);
                    f.completeExceptionally(e);
                }
            }
        }, "FriendAction").start();
        return f;
    }

    private void handleResult(FriendsService.ResultCode r) {
        if (r == FriendsService.ResultCode.SUCCESS) {
            remoteFriendListUpdateHandler.refreshNow();
        } else {
            showFailureToast(r);
        }
    }

    private void showFailureToast(FriendsService.ResultCode r) {
        bridge.notifyToast("friend.action_failed", r.name(), null);
    }

    // -------- toggles --------

    public boolean isFriendListEnabled() { return friendListEnabled; }
    public void setFriendListEnabled(boolean v) {
        if (friendListEnabled == v) return;
        friendListEnabled = v;
        if (v && onlineMode) remoteFriendListUpdateHandler.start();
        if (!v) remoteFriendListUpdateHandler.stop();
        updateSettings();
    }

    public boolean isAllowFriendRequests() { return allowFriendRequests; }
    public void setAllowFriendRequests(boolean v) {
        if (allowFriendRequests == v) return;
        allowFriendRequests = v;
        updateSettings();
    }

    private void updateSettings() {
        updateFriendSettings(friendListEnabled, allowFriendRequests);
    }

    // -------- block list --------

    public void hidePlayer(UUID id) { hiddenPlayers.add(id); }
    public void showPlayer(UUID id) { hiddenPlayers.remove(id); }
    public boolean shouldHideMessageFrom(UUID id) { return hiddenPlayers.contains(id); }
    public boolean isHidden(UUID id) { return hiddenPlayers.contains(id); }
    public boolean isBlocked(UUID id) { return hiddenPlayers.contains(id); }
    public Set<UUID> getHiddenPlayers() { return Collections.unmodifiableSet(hiddenPlayers); }

    // -------- name discovery --------

    public void discoverName(UUID profileId, String name) {
        discoveredNamesToUUID.put(name, profileId);
    }

    public UUID getProfileIdFromName(String name) {
        return discoveredNamesToUUID.get(name);
    }

    private static List<PlayerData> remap(Set<FriendDto> set) {
        List<PlayerData> result = new ArrayList<PlayerData>();
        for (FriendDto f : set) {
            result.add(new PlayerData(f.profileId(), f.name()));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Simple name/id pair. Replaces Java 17 record.
     */
    public static class PlayerData {
        private final UUID id;
        private final String name;
        public PlayerData(UUID id, String name) { this.id = id; this.name = name; }
        public UUID id() { return id; }
        public String name() { return name; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlayerData)) return false;
            PlayerData that = (PlayerData) o;
            return id.equals(that.id);
        }
        @Override public int hashCode() { return id.hashCode(); }
    }
}
