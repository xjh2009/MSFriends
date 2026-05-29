package dev.msf.friends.authlib.response;

import java.util.Objects;
import java.util.UUID;

/** Backport of authlib 7.0.72 PresenceStatusDto (Java 8 compatible). */
public final class PresenceStatusDto {
    private final UUID profileId;
    private final UUID pmid;
    private final PresenceStatus status;
    private final JoinInfo joinInfo;
    private final long lastUpdatedEpochMilli;

    public PresenceStatusDto(UUID profileId, UUID pmid, PresenceStatus status, JoinInfo joinInfo, long lastUpdatedEpochMilli) {
        this.profileId = profileId;
        this.pmid = pmid;
        this.status = status;
        this.joinInfo = joinInfo;
        this.lastUpdatedEpochMilli = lastUpdatedEpochMilli;
    }

    public UUID profileId() { return profileId; }
    public UUID pmid() { return pmid; }
    public PresenceStatus status() { return status; }
    public JoinInfo joinInfo() { return joinInfo; }
    public long lastUpdatedEpochMilli() { return lastUpdatedEpochMilli; }

    public static final class JoinInfo {
        private final String value;
        private final boolean invited;

        public JoinInfo(String value, boolean invited) {
            this.value = value;
            this.invited = invited;
        }

        public String value() { return value; }
        public boolean invited() { return invited; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JoinInfo)) return false;
            JoinInfo that = (JoinInfo) o;
            return invited == that.invited && Objects.equals(value, that.value);
        }
        @Override public int hashCode() { return Objects.hash(value, invited); }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PresenceStatusDto)) return false;
        PresenceStatusDto that = (PresenceStatusDto) o;
        return Objects.equals(profileId, that.profileId) && Objects.equals(pmid, that.pmid) && Objects.equals(status, that.status) && Objects.equals(joinInfo, that.joinInfo);
    }
    @Override public int hashCode() { return Objects.hash(profileId, pmid, status, joinInfo); }
}
