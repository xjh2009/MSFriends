package com.mojang.authlib.yggdrasil.response;

import java.util.Collections;
import java.util.Set;

public class FriendData {
    private final Set<FriendDto> friends;
    private final Set<FriendDto> incomingRequests;
    private final Set<FriendDto> outgoingRequests;

    public FriendData(Set<FriendDto> friends, Set<FriendDto> incomingRequests, Set<FriendDto> outgoingRequests) {
        this.friends = Collections.unmodifiableSet(friends);
        this.incomingRequests = Collections.unmodifiableSet(incomingRequests);
        this.outgoingRequests = Collections.unmodifiableSet(outgoingRequests);
    }

    public static FriendData empty() {
        return new FriendData(Collections.<FriendDto>emptySet(), Collections.<FriendDto>emptySet(), Collections.<FriendDto>emptySet());
    }

    public Set<FriendDto> friends() { return friends; }
    public Set<FriendDto> incomingRequests() { return incomingRequests; }
    public Set<FriendDto> outgoingRequests() { return outgoingRequests; }
}
