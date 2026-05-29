package com.mojang.authlib.yggdrasil.request;

import java.util.List;
import java.util.UUID;

/**
 * Join info update request. Java 8 compatible (no record).
 */
public class JoinInfoUpdate {
    private final String value;
    private final List<UUID> invites;

    public JoinInfoUpdate(String value, List<UUID> invites) {
        this.value = value;
        this.invites = invites;
    }

    public String value() { return value; }
    public List<UUID> invites() { return invites; }
}
