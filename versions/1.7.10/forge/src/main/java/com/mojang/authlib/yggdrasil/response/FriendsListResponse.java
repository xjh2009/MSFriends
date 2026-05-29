package com.mojang.authlib.yggdrasil.response;

import java.util.List;

public class FriendsListResponse {
    private final List<FriendDto> friends;
    private final List<FriendDto> incomingRequests;
    private final List<FriendDto> outgoingRequests;

    public FriendsListResponse(List<FriendDto> friends, List<FriendDto> incomingRequests, List<FriendDto> outgoingRequests) {
        this.friends = friends;
        this.incomingRequests = incomingRequests;
        this.outgoingRequests = outgoingRequests;
    }

    public List<FriendDto> getFriends() { return friends; }
    public List<FriendDto> getIncomingRequests() { return incomingRequests; }
    public List<FriendDto> getOutgoingRequests() { return outgoingRequests; }
}
