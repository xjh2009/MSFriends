package com.mojang.authlib.yggdrasil.response;

import java.util.UUID;

/**
 * Friend DTO. Java 8 compatible (no record).
 */
public class FriendDto {
    private final UUID profileId;
    private final String name;

    public FriendDto(UUID profileId, String name) {
        this.profileId = profileId;
        this.name = name;
    }

    public UUID profileId() { return profileId; }
    public String name() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FriendDto)) return false;
        FriendDto that = (FriendDto) o;
        return profileId != null ? profileId.equals(that.profileId) : that.profileId == null;
    }

    @Override
    public int hashCode() {
        return profileId != null ? profileId.hashCode() : 0;
    }
}
