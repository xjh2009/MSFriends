package com.mojang.authlib.yggdrasil.response;

import java.util.List;

/**
 * Backport of authlib 7.0.72 FriendData record.
 */
public record FriendData(
        List<FriendDto> friends,
        List<FriendDto> incomingRequests,
        List<FriendDto> outgoingRequests
) {
    public static FriendData empty() {
        return new FriendData(List.of(), List.of(), List.of());
    }
}
