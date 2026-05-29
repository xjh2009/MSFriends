package com.mojang.authlib.yggdrasil.request;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class JoinInfoUpdate {
    private final String value;
    private final Set<UUID> invites;

    public JoinInfoUpdate(String value, Set<UUID> invites) {
        this.value = value;
        this.invites = Collections.unmodifiableSet(invites);
    }

    public String value() { return value; }
    public Set<UUID> invites() { return invites; }
}
