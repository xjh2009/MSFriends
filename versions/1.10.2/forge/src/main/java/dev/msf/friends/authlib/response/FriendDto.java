package dev.msf.friends.authlib.response;

import java.util.Objects;
import java.util.UUID;

/** Backport of authlib 7.0.72 FriendDto (Java 8 compatible). */
public final class FriendDto {
    private final UUID profileId;
    private final String name;

    public FriendDto(UUID profileId, String name) {
        this.profileId = profileId;
        this.name = name;
    }

    public UUID profileId() { return profileId; }
    public String name() { return name; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FriendDto)) return false;
        FriendDto that = (FriendDto) o;
        return Objects.equals(profileId, that.profileId) && Objects.equals(name, that.name);
    }
    @Override public int hashCode() { return Objects.hash(profileId, name); }
    @Override public String toString() { return "FriendDto[profileId=" + profileId + ", name=" + name + "]"; }
}
