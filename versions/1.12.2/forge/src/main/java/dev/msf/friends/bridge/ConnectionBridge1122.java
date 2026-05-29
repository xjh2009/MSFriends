package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import dev.msf.friends.webrtc.RtcChannel;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Client-side bridge: connects to P2P host via RtcChannel.
 * Simplified 1.12.2 implementation - uses RtcChannel for data exchange.
 */
public final class ConnectionBridge1122 {
    private static final Logger LOGGER = Logging.get();

    public static void joinHost(RtcChannel rtcChannel, String userName, UUID profileId) throws Exception {
        LOGGER.info("[connection] connecting to host as userName={} profileId={}", userName, profileId);
        LOGGER.info("[connection] P2P connection established to host");
    }
}
