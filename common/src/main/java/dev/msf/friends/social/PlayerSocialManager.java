package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Strict 26.2 port of {@code PlayerSocialManager}. */
public final class PlayerSocialManager {
    private static final Logger LOGGER = Logging.get();

    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final RemoteFriendListUpdateHandler remoteFriendListUpdateHandler;
    private final PresenceHandler presenceHandler;
    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private final Map<String, UUID> discoveredNamesToUUID = new HashMap<>();
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
        this.presenceHandler = new PresenceHandler(bridge, friendsService, () -> this);
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

    public boolean isFriendsPmid(@Nullable UUID pmid) {
        if (pmid == null) return false;
        UUID profileId = presenceHandler.getProfileIdFromPmid(pmid);
        return profileId != null && getFriends().stream().anyMatch(p -> p.id().equals(profileId));
    }

    public boolean isFriend(UUID profileId) {
        for (PlayerData p : getFriends()) if (p.id().equals(profileId)) return true;
        return false;
    }

    // -------- block list (vanilla bookkeeping retained) --------

    public void hidePlayer(UUID id) { hiddenPlayers.add(id); }
    public void showPlayer(UUID id) { hiddenPlayers.remove(id); }
    public boolean shouldHideMessageFrom(UUID id) { return isHidden(id) || isBlocked(id); }
    public boolean isHidden(UUID id) { return hiddenPlayers.contains(id); }
    public boolean isBlocked(UUID id) { return false; /* no UserApiService block list in our backend */ }
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
        return runAction(() -> friendsService.sendFriendRequest(name));
    }
    public CompletableFuture<FriendsService.ResultCode> sendFriendRequest(UUID id) {
        return runAction(() -> friendsService.sendFriendRequest(id));
    }
    public CompletableFuture<FriendsService.ResultCode> removeFriend(UUID id) {
        return runAction(() -> friendsService.removeFriend(id));
    }
    public CompletableFuture<FriendsService.ResultCode> acceptIncomingFriendRequest(UUID id) {
        return runAction(() -> friendsService.acceptIncomingFriendRequest(id));
    }
    public CompletableFuture<FriendsService.ResultCode> declineIncomingFriendRequest(UUID id) {
        return runAction(() -> friendsService.declineIncomingFriendRequest(id));
    }
    public CompletableFuture<FriendsService.ResultCode> revokeOutgoingFriendRequest(UUID id) {
        return runAction(() -> friendsService.revokeOutgoingFriendRequest(id));
    }
    public CompletableFuture<FriendsService.ResultCode> updateFriendSettings(boolean fle, boolean afr) {
        return runAction(() -> {
            FriendsService.ResultCode r = friendsService.updateFriendSettings(fle, afr);
            if (r == FriendsService.ResultCode.SUCCESS) {
                this.friendListEnabled = fle;
                this.allowFriendRequests = afr;
            }
            return r;
        });
    }

    private CompletableFuture<FriendsService.ResultCode> runAction(Supplier<FriendsService.ResultCode> action) {
        return CompletableFuture.supplyAsync(() -> {
            FriendsService.ResultCode r = action.get();
            handleResult(r);
            return r;
        }).thenComposeAsync(r -> r == FriendsService.ResultCode.SUCCESS
                ? remoteFriendListUpdateHandler.forceUpdate().thenApply(u -> r)
                : CompletableFuture.completedFuture(r));
    }

    private void handleResult(FriendsService.ResultCode r) {
        if (r != FriendsService.ResultCode.SUCCESS) showFailureToast(r);
    }

    private void showFailureToast(FriendsService.ResultCode r) {
        String key = switch (r) {
            case TOO_MANY_REQUESTS  -> "friend.action.rate_limited";
            case UNKNOWN_PROFILE    -> "friend.action.unknown_profile";
            case FORBIDDEN          -> "friend.action.forbidden";
            case SERVICE_NOT_AVAILABLE -> "friend.action.unavailable";
            case ERROR              -> "friend.action.failed";
            default -> null;
        };
        if (key == null) return;
        bridge.executeOnClientThread(() -> bridge.notifyToast(key, "", null));
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
        return list.stream().map(f -> new PlayerData(f.profileId(), f.name())).toList();
    }

    public record PlayerData(UUID id, String name) {}
}
