package com.mojang.authlib.yggdrasil;

/**
 * Friends service interface. Java 8 compatible version.
 */
public interface FriendsService {
    ResultCode sendFriendRequest(String name);
    ResultCode sendFriendRequest(java.util.UUID id);
    ResultCode removeFriend(java.util.UUID id);
    ResultCode acceptIncomingFriendRequest(java.util.UUID id);
    ResultCode declineIncomingFriendRequest(java.util.UUID id);
    ResultCode revokeOutgoingFriendRequest(java.util.UUID id);
    ResultCode updateFriendSettings(boolean friendListEnabled, boolean allowRequests);
    com.mojang.authlib.yggdrasil.response.FriendsListResponse getFriendList();
    com.mojang.authlib.yggdrasil.response.PresenceResponse getPresence();
    void updatePresence(com.mojang.authlib.yggdrasil.request.JoinInfoUpdate update);

    enum ResultCode {
        SUCCESS,
        TOO_MANY_REQUESTS,
        NOT_FOUND,
        ALREADY_EXISTS,
        CANNOT_ADD_SELF,
        INVALID_REQUEST,
        UNKNOWN_ERROR
    }
}
