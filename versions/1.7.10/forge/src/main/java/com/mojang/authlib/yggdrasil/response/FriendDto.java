package com.mojang.authlib.yggdrasil.response;

import java.util.UUID;

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
        return profileId.equals(that.profileId);
    }

    @Override
    public int hashCode() {
        return profileId.hashCode();
    }

    @Override
    public String toString() {
        return "FriendDto{profileId=" + profileId + ", name='" + name + "'}";
    }
}
