package com.mojang.authlib.yggdrasil.response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PresenceResponse {
    private final List<PresenceStatusDto> presence;

    public PresenceResponse(List<PresenceStatusDto> presence) {
        this.presence = Collections.unmodifiableList(new ArrayList<>(presence));
    }

    public List<PresenceStatusDto> presence() { return presence; }
}
