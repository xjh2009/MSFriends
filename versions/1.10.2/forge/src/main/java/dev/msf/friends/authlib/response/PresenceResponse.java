package dev.msf.friends.authlib.response;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Backport of authlib 7.0.72 PresenceResponse (Java 8 compatible). */
public final class PresenceResponse {
    private final List<PresenceStatusDto> presence;

    public PresenceResponse(List<PresenceStatusDto> presence) {
        this.presence = presence != null ? presence : Collections.<PresenceStatusDto>emptyList();
    }

    public List<PresenceStatusDto> presence() { return presence; }

    public static PresenceResponse empty() {
        return new PresenceResponse(Collections.<PresenceStatusDto>emptyList());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PresenceResponse)) return false;
        PresenceResponse that = (PresenceResponse) o;
        return Objects.equals(presence, that.presence);
    }
    @Override public int hashCode() { return Objects.hash(presence); }
}
