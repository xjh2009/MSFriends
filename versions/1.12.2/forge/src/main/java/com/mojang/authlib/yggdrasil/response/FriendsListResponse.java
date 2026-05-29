package com.mojang.authlib.yggdrasil.response;

import java.util.List;

/**
 * Friends list API response. Java 8 compatible (no record).
 */
public class FriendsListResponse {
    private final List<FriendDto> friends;
    private final List<FriendDto> incomingRequests;
    private final List<FriendDto> outgoingRequests;

    public FriendsListResponse(List<FriendDto> friends, List<FriendDto> incomingRequests, List<FriendDto> outgoingRequests) {
        this.friends = friends;
        this.incomingRequests = incomingRequests;
        this.outgoingRequests = outgoingRequests;
    }

    public List<FriendDto> friends() { return friends; }
    public List<FriendDto> incomingRequests() { return incomingRequests; }
    public List<FriendDto> outgoingRequests() { return outgoingRequests; }
}
