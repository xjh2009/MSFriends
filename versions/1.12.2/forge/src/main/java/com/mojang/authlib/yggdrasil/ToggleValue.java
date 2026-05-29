package com.mojang.authlib.yggdrasil;

/**
 * Toggle value enum. Java 8 compatible.
 */
public enum ToggleValue {
    ENABLED,
    DISABLED;

    public boolean isEnabled() { return this == ENABLED; }
}
