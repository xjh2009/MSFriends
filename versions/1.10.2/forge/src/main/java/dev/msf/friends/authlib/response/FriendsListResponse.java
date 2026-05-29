package dev.msf.friends.authlib.response;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Backport of authlib 7.0.72 FriendsListResponse (Java 8 compatible). */
public final class FriendsListResponse {
    private final List<FriendDto> friends;
    private final List<FriendDto> incomingRequests;
    private final List<FriendDto> outgoingRequests;

    public FriendsListResponse(List<FriendDto> friends, List<FriendDto> incomingRequests, List<FriendDto> outgoingRequests) {
        this.friends = friends != null ? friends : Collections.<FriendDto>emptyList();
        this.incomingRequests = incomingRequests != null ? incomingRequests : Collections.<FriendDto>emptyList();
        this.outgoingRequests = outgoingRequests != null ? outgoingRequests : Collections.<FriendDto>emptyList();
    }

    public List<FriendDto> friends() { return friends; }
    public List<FriendDto> incomingRequests() { return incomingRequests; }
    public List<FriendDto> outgoingRequests() { return outgoingRequests; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FriendsListResponse)) return false;
        FriendsListResponse that = (FriendsListResponse) o;
        return Objects.equals(friends, that.friends) && Objects.equals(incomingRequests, that.incomingRequests) && Objects.equals(outgoingRequests, that.outgoingRequests);
    }
    @Override public int hashCode() { return Objects.hash(friends, incomingRequests, outgoingRequests); }
}
