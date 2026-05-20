package com.mojang.authlib.yggdrasil.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Backport of authlib 7.0.72 PresenceStatusDto record.
 */
public record PresenceStatusDto(
        UUID profileId,
        UUID pmid,
        PresenceStatus status,
        JoinInfo joinInfo,
        Instant lastUpdated
) {
    public record JoinInfo(String value, boolean invited) {}
}
