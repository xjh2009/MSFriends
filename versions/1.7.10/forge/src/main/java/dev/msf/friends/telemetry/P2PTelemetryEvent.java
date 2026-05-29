package dev.msf.friends.telemetry;

/**
 * P2P telemetry event types (1.7.10 / Java 8 port).
 */
public final class P2PTelemetryEvent {
    public static final P2PTelemetryEvent INSTANCE = new P2PTelemetryEvent();

    private P2PTelemetryEvent() {}

    public enum FailureStage {
        NONE,
        SIGNALING,
        ICE_CONNECT,
        TIMEOUT,
        DATA_CHANNEL
    }

    public enum IceCandidateType {
        UNKNOWN,
        HOST,
        SRFLX,
        RELAY;

        public static IceCandidateType byName(String name) {
            if (name == null) return null;
            switch (name.toLowerCase()) {
                case "host":   return HOST;
                case "srflx":  return SRFLX;
                case "relay":  return RELAY;
                default:       return null;
            }
        }
    }

    public void send(boolean success, State state, java.time.Instant start, java.time.Instant signalingDone, java.time.Instant end) {
        // In 1.7.10, telemetry is a no-op — just log
        long totalMs = end.toEpochMilli() - start.toEpochMilli();
        dev.msf.friends.util.Logging.get(P2PTelemetryEvent.class)
            .info("[telemetry] P2P {} in {}ms, failure={}", success ? "success" : "failure", totalMs, state.failureStage);
    }

    public static class State {
        private volatile FailureStage failureStage = FailureStage.NONE;
        private volatile IceCandidateType localIce = IceCandidateType.UNKNOWN;
        private volatile IceCandidateType remoteIce = IceCandidateType.UNKNOWN;

        public FailureStage failureStage() { return failureStage; }
        public void setFailureStage(FailureStage stage) { this.failureStage = stage; }
        public void setIceInfo(IceCandidateType local, IceCandidateType remote) {
            this.localIce = local;
            this.remoteIce = remote;
        }
    }
}
