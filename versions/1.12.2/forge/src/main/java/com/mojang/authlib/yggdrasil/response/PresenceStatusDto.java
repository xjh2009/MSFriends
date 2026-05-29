package com.mojang.authlib.yggdrasil.response;

import java.util.UUID;

/**
 * Presence status DTO. Java 8 compatible (no record).
 */
public class PresenceStatusDto {
    private final UUID profileId;
    private final String name;
    private final PresenceStatus status;
    private final JoinInfo joinInfo;

    public PresenceStatusDto(UUID profileId, String name, PresenceStatus status, JoinInfo joinInfo) {
        this.profileId = profileId;
        this.name = name;
        this.status = status;
        this.joinInfo = joinInfo;
    }

    public UUID profileId() { return profileId; }
    public String name() { return name; }
    public PresenceStatus status() { return status; }
    public JoinInfo joinInfo() { return joinInfo; }

    /**
     * Join info inner DTO. Java 8 compatible (no record).
     */
    public static class JoinInfo {
        private final String value;
        private final java.util.List<UUID> invites;

        public JoinInfo(String value, java.util.List<UUID> invites) {
            this.value = value;
            this.invites = invites;
        }

        public String value() { return value; }
        public java.util.List<UUID> invites() { return invites; }
    }
}
