package com.mojang.authlib.yggdrasil;

import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.FriendData;
import com.mojang.authlib.yggdrasil.response.PresenceResponse;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Backport of authlib 7.0.72 FriendsService interface.
 * Bundled in this mod for compatibility with authlib 7.0.63 runtimes.
 */
public interface FriendsService {

    ResultCode getFriendData(Consumer<FriendData> consumer);

    ResultCode removeFriend(UUID profileId);

    ResultCode acceptIncomingFriendRequest(UUID profileId);

    ResultCode declineIncomingFriendRequest(UUID profileId);

    ResultCode sendFriendRequest(String name);

    ResultCode sendFriendRequest(UUID profileId);

    ResultCode revokeOutgoingFriendRequest(UUID profileId);

    ResultCode updateFriendSettings(boolean friendsEnabled, boolean acceptInvites);

    PresenceResponse presence(String status, JoinInfoUpdate joinInfo);

    // -------------------------------------------------------------------------

    enum ResultCode {
        SUCCESS,
        ERROR,
        SERVICE_NOT_AVAILABLE,
        TOO_MANY_REQUESTS,
        FORBIDDEN,
        UPGRADE_NEEDED,
        CONNECTION_ISSUE,
        TEMPORARY_UNAVAILABLE,
        UNKNOWN_PROFILE,
        GENERIC_ERROR
    }

    record PlayerData(UUID id, String name) {}
}
