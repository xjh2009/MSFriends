package com.mojang.authlib.yggdrasil;

public enum ToggleValue {
    ENABLED,
    DISABLED;

    public static ToggleValue fromString(String s) {
        if ("enabled".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) return ENABLED;
        return DISABLED;
    }
}
