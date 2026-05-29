package dev.msf.friends.authlib;

/** Backport of authlib 7.0.72 ChatToggleValue enum (Java 8 compatible). */
public enum ChatToggleValue {
    DISABLED,
    FRIENDS_ONLY,
    ENABLED;

    public boolean isEnabled() { return this == ENABLED; }
    public boolean isFriendsOnly() { return this == FRIENDS_ONLY; }
    public boolean isDisabled() { return this == DISABLED; }
}
