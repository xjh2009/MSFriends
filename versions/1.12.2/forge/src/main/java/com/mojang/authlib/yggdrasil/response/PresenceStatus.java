package com.mojang.authlib.yggdrasil.response;

/**
 * Presence status enum. Java 8 compatible.
 */
public enum PresenceStatus {
    OFFLINE,
    ONLINE,
    IN_GAME,
    HOSTING;

    public String getSerializedName() { return name(); }
    public static PresenceStatus byName(String s) {
        try { return valueOf(s); }
        catch (Exception e) { return OFFLINE; }
    }
}
