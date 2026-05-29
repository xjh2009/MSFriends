package com.mojang.authlib.yggdrasil.response;

import java.util.ArrayList;
import java.util.List;

/**
 * Presence API response. Java 8 compatible (no record).
 */
public class PresenceResponse {
    private final List<PresenceStatusDto> statuses;

    public PresenceResponse(List<PresenceStatusDto> statuses) {
        this.statuses = statuses;
    }

    public List<PresenceStatusDto> statuses() { return statuses; }
}
