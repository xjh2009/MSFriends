package com.mojang.authlib.yggdrasil.response;

import java.util.UUID;

public class PresenceStatusDto {
    private final UUID profileId;
    private final UUID pmid;
    private final PresenceStatus status;
    private final JoinInfo joinInfo;
    private final String lastUpdated;

    public PresenceStatusDto(UUID profileId, UUID pmid, PresenceStatus status, JoinInfo joinInfo, String lastUpdated) {
        this.profileId = profileId;
        this.pmid = pmid;
        this.status = status;
        this.joinInfo = joinInfo;
        this.lastUpdated = lastUpdated;
    }

    public UUID profileId() { return profileId; }
    public UUID pmid() { return pmid; }
    public PresenceStatus status() { return status; }
    public JoinInfo joinInfo() { return joinInfo; }
    public String lastUpdated() { return lastUpdated; }

    public static class JoinInfo {
        private final String value;
        private final boolean invited;

        public JoinInfo(String value, boolean invited) {
            this.value = value;
            this.invited = invited;
        }

        public String value() { return value; }
        public boolean invited() { return invited; }
    }
}
