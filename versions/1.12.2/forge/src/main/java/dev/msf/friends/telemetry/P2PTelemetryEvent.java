package dev.msf.friends.telemetry;

import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

/**
 * P2PTelemetryEvent - Java 8 compatible version.
 */
public class P2PTelemetryEvent {
    private static final Logger LOGGER = Logging.get();
    public static final P2PTelemetryEvent INSTANCE = new P2PTelemetryEvent();

    public enum FailureStage { SIGNALING, ICE_CONNECT, TIMEOUT, NONE }

    public static class State {
        private FailureStage failureStage = FailureStage.NONE;
        private String localIceInfo = "";
        private String remoteIceInfo = "";

        public void setFailureStage(FailureStage stage) { this.failureStage = stage; }
        public void setIceInfo(String local, String remote) {
            this.localIceInfo = local; this.remoteIceInfo = remote;
        }
    }

    public void send(boolean success, State state, Instant start, Instant signalingDone, Instant end) {
        long totalMs = end.toEpochMilli() - start.toEpochMilli();
        if (success) {
            LOGGER.info("[telemetry] P2P handshake succeeded in {}ms", totalMs);
        } else {
            LOGGER.info("[telemetry] P2P handshake failed at stage {} in {}ms", state.failureStage, totalMs);
        }
    }
}
