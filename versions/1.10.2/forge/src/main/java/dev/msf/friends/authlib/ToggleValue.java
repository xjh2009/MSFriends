package dev.msf.friends.authlib;

/** Backport of authlib 7.0.72 ToggleValue enum (Java 8 compatible). */
public enum ToggleValue {
    DISABLED,
    ENABLED;

    public boolean isEnabled() { return this == ENABLED; }
    public boolean isDisabled() { return this == DISABLED; }
}
