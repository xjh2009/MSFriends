package dev.msf.friends.authlib;

import dev.msf.friends.authlib.request.JoinInfoUpdate;
import dev.msf.friends.authlib.response.FriendData;
import dev.msf.friends.authlib.response.PresenceResponse;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ported from authlib 7.0.72 FriendsService interface (Java 8 compatible).
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

    enum ResultCode {
        SUCCESS, ERROR, SERVICE_NOT_AVAILABLE, TOO_MANY_REQUESTS, FORBIDDEN,
        UPGRADE_NEEDED, CONNECTION_ISSUE, TEMPORARY_UNAVAILABLE, UNKNOWN_PROFILE, GENERIC_ERROR
    }
}
