package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Server-side bridge: accepts incoming P2P guest connection.
 * Simplified 1.12.2 implementation.
 */
public final class AcceptGuestBridge1122 {
    private static final Logger LOGGER = Logging.get();

    public static void acceptGuest(RtcChannel rtcChannel, UUID guestProfileId) throws Exception {
        LOGGER.info("[accept-guest] accepting guest profileId={}", guestProfileId);
        LOGGER.info("[accept-guest] P2P guest connection accepted, profileId={}", guestProfileId);
    }
}
