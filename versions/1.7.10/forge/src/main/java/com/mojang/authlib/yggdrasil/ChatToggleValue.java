package com.mojang.authlib.yggdrasil;

public enum ChatToggleValue {
    DISABLED,
    FRIENDS_ONLY,
    ENABLED;

    public static ChatToggleValue fromString(String s) {
        if ("enabled".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) return ENABLED;
        if ("friends_only".equalsIgnoreCase(s)) return FRIENDS_ONLY;
        return DISABLED;
    }
}
