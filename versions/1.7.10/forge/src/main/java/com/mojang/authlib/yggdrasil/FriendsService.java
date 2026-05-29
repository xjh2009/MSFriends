package com.mojang.authlib.yggdrasil;

import com.mojang.authlib.yggdrasil.response.FriendData;
import com.mojang.authlib.yggdrasil.response.PresenceResponse;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;

import java.util.UUID;

/**
 * Backported friends service interface for authlib 1.5.21 (MC 1.7.10).
 */
public interface FriendsService {

    enum ResultCode {
        SUCCESS,
        CONNECTION_ISSUE,
        UPGRADE_NEEDED,
        UNKNOWN_PROFILE,
        TEMPORARY_UNAVAILABLE,
        FORBIDDEN,
        SERVICE_NOT_AVAILABLE,
        TOO_MANY_REQUESTS,
        GENERIC_ERROR,
        ERROR
    }

    /** Async callback-based */
    ResultCode getFriendData(FriendDataCallback callback);

    /** Synchronous convenience method */
    FriendData getFriendData();

    void addFriend(String name);
    ResultCode removeFriend(UUID profileId);
    void acceptRequest(UUID profileId);
    void declineRequest(UUID profileId);
    ResultCode blockPlayer(UUID profileId);

    // Methods used by PlayerSocialManager
    ResultCode sendFriendRequest(String name);
    ResultCode sendFriendRequest(UUID profileId);
    ResultCode acceptFriendRequest(UUID profileId);
    ResultCode declineFriendRequest(UUID profileId);
    ResultCode updateFriendSettings(boolean friendListEnabled, boolean allowFriendRequests);

    ToggleValue getFriendInvitesToggle();
    void setFriendInvitesToggle(ToggleValue value);

    ChatToggleValue getChatToggle();
    void setChatToggle(ChatToggleValue value);

    PresenceResponse presence(String status, JoinInfoUpdate joinInfo);

    interface FriendDataCallback {
        void accept(FriendData data);
    }
}
