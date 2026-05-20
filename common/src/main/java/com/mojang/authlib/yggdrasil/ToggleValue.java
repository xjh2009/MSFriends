package com.mojang.authlib.yggdrasil;

/**
 * Backport of authlib 7.0.72 ToggleValue enum.
 */
public enum ToggleValue {
    DISABLED,
    ENABLED;

    public boolean isEnabled() {
        return this == ENABLED;
    }

    public boolean isDisabled() {
        return this == DISABLED;
    }
}
