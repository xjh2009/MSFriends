package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * PlayerSocialManager - Java 8 compatible version.
 */
public final class PlayerSocialManager {
    private static final Logger LOGGER = Logging.get();

    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final RemoteFriendListUpdateHandler remoteFriendListUpdateHandler;
    private final PresenceHandler presenceHandler;
    private final Set<UUID> hiddenPlayers = new HashSet<>();
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

    public int getFriendCount() {
        return getFriends().size();
    }

    public List<String> getFriendNames() {
        List<String> names = new ArrayList<>();
        for (PlayerData p : getFriends()) {
            names.add(p.name());
        }
        return names;
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
    public boolean isHidden(UUID id) { return hiddenPlayers.contains(id); }
    public Set<UUID> getHiddenPlayers() { return hiddenPlayers; }

    public UUID getDiscoveredUUID(String name) {
        UUID uuid = discoveredNamesToUUID.get(name);
        return uuid != null ? uuid : new UUID(0L, 0L);
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

    public CompletableFuture<Boolean> sendFriendRequest(String name) {
        return CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override public Boolean get() {
                FriendsService.ResultCode r = friendsService.sendFriendRequest(name);
                if (r == FriendsService.ResultCode.SUCCESS) {
                    remoteFriendListUpdateHandler.forceUpdate();
                    return true;
                }
                LOGGER.warn("[social] sendFriendRequest({}) failed: {}", name, r);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> sendFriendRequest(UUID id) {
        return CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override public Boolean get() {
                FriendsService.ResultCode r = friendsService.sendFriendRequest(id);
                if (r == FriendsService.ResultCode.SUCCESS) {
                    remoteFriendListUpdateHandler.forceUpdate();
                    return true;
                }
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> removeFriend(UUID id) {
        return CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override public Boolean get() {
                FriendsService.ResultCode r = friendsService.removeFriend(id);
                if (r == FriendsService.ResultCode.SUCCESS) {
                    remoteFriendListUpdateHandler.forceUpdate();
                    return true;
                }
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> acceptIncomingFriendRequest(UUID id) {
        return CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override public Boolean get() {
                FriendsService.ResultCode r = friendsService.acceptIncomingFriendRequest(id);
                if (r == FriendsService.ResultCode.SUCCESS) {
                    remoteFriendListUpdateHandler.forceUpdate();
                    return true;
                }
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> declineIncomingFriendRequest(UUID id) {
        return CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override public Boolean get() {
                FriendsService.ResultCode r = friendsService.declineIncomingFriendRequest(id);
                if (r == FriendsService.ResultCode.SUCCESS) {
                    remoteFriendListUpdateHandler.forceUpdate();
                    return true;
                }
                return false;
            }
        });
    }

    private List<PlayerData> remap(List<FriendDto> dtos) {
        List<PlayerData> result = new ArrayList<>();
        for (FriendDto dto : dtos) {
            discoveredNamesToUUID.put(dto.name(), dto.profileId());
            boolean online = presenceHandler.isOnline(dto.profileId());
            result.add(new PlayerData(dto.profileId(), dto.name(), online));
        }
        return result;
    }

    /**
     * Immutable player data. Java 8 compatible (no record).
     */
    public static class PlayerData {
        private final UUID id;
        private final String name;
        private final boolean online;

        public PlayerData(UUID id, String name, boolean online) {
            this.id = id;
            this.name = name;
            this.online = online;
        }

        public UUID id() { return id; }
        public String name() { return name; }
        public boolean isOnline() { return online; }
    }
}
