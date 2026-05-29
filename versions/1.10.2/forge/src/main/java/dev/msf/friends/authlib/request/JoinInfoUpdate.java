package dev.msf.friends.authlib.request;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Backport of authlib 7.0.72 JoinInfoUpdate (Java 8 compatible). */
public final class JoinInfoUpdate {
    private final String value;
    private final Set<UUID> invites;

    public JoinInfoUpdate(String value, Set<UUID> invites) {
        this.value = value;
        this.invites = invites != null ? invites : Collections.<UUID>emptySet();
    }

    public String value() { return value; }
    public Set<UUID> invites() { return invites; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JoinInfoUpdate)) return false;
        JoinInfoUpdate that = (JoinInfoUpdate) o;
        return Objects.equals(value, that.value) && Objects.equals(invites, that.invites);
    }
    @Override public int hashCode() { return Objects.hash(value, invites); }
}
