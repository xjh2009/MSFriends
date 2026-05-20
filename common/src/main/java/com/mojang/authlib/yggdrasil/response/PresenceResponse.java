package com.mojang.authlib.yggdrasil.response;

import java.util.List;

/**
 * Backport of authlib 7.0.72 PresenceResponse record.
 */
public record PresenceResponse(List<PresenceStatusDto> presence) {

    public static PresenceResponse empty() {
        return new PresenceResponse(List.of());
    }
}
