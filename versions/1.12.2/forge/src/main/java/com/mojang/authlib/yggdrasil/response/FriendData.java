package com.mojang.authlib.yggdrasil.response;

import java.util.List;
import java.util.UUID;

/**
 * Friend data DTO. Java 8 compatible (no record).
 */
public class FriendData {
    private final List<FriendDto> friends;
    private final List<FriendDto> incomingRequests;
    private final List<FriendDto> outgoingRequests;

    public FriendData(List<FriendDto> friends, List<FriendDto> incomingRequests, List<FriendDto> outgoingRequests) {
        this.friends = friends;
        this.incomingRequests = incomingRequests;
        this.outgoingRequests = outgoingRequests;
    }

    public static FriendData empty() {
        return new FriendData(
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList()
        );
    }

    public List<FriendDto> friends() { return friends; }
    public List<FriendDto> incomingRequests() { return incomingRequests; }
    public List<FriendDto> outgoingRequests() { return outgoingRequests; }
}
