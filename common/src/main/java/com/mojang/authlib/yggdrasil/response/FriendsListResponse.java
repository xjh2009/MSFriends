package com.mojang.authlib.yggdrasil.response;

import java.util.List;

/**
 * Backport of authlib 7.0.72 FriendsListResponse record.
 * Used internally by YggdrasilFriendsService for JSON deserialization.
 */
public record FriendsListResponse(
        List<FriendDto> friends,
        List<FriendDto> incomingRequests,
        List<FriendDto> outgoingRequests
) {}
