package dev.msf.friends.telemetry;

import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class P2PTelemetryEvent {
    private static final Logger LOGGER = Logging.get();
    public static final P2PTelemetryEvent INSTANCE = new P2PTelemetryEvent();

    public void send(boolean successful, State state, Instant connectionStartTime,
                     Instant signalingDoneTime, Instant connectionEstablishedTime) {
        State.Snapshot s = state.snapshot();
        Long totalMs = millisBetween(connectionStartTime, connectionEstablishedTime);
        Long signalingMs = millisBetween(connectionStartTime, signalingDoneTime);
        Long iceConnectMs = millisBetween(signalingDoneTime, connectionEstablishedTime);
        IcePath path = null;
        if (s.localCandidateType() != null && s.remoteCandidateType() != null) {
            path = IcePath.classify(s.localCandidateType(), s.remoteCandidateType());
        }
        LOGGER.info("[telemetry] P2P_CONNECTION successful={} icePath={} local={} remote={} totalMs={} signalingMs={} iceConnectMs={} failureStage={}",
                successful, path, s.localCandidateType(), s.remoteCandidateType(),
                totalMs, signalingMs, iceConnectMs, successful ? null : s.failureStage());
    }

    private static Long millisBetween(Instant from, Instant to) {
        return (from != null && to != null) ? from.until(to, ChronoUnit.MILLIS) : null;
    }

    public enum FailureStage {
        SIGNALING, ICE_CONNECT, TIMEOUT;
        public String getSerializedName() { return name(); }
    }

    public enum IceCandidateType {
        HOST("host"), SRFLX("srflx"), PRFLX("prflx"), RELAY("relay");
        private static final Map<String, IceCandidateType> BY_NAME;
        static {
            Map<String, IceCandidateType> m = new HashMap<>();
            for (IceCandidateType t : values()) m.put(t.getSerializedName(), t);
            BY_NAME = m;
        }
        private final String name;
        IceCandidateType(String name) { this.name = name; }
        public String getSerializedName() { return name; }
        public static Optional<IceCandidateType> byName(String name) {
            return Optional.ofNullable(BY_NAME.get(name));
        }
    }

    public enum IcePath {
        LOCAL, DIRECT, RELAY, UNKNOWN;
        public String getSerializedName() { return name(); }
        public static IcePath classify(IceCandidateType local, IceCandidateType remote) {
            if (local == IceCandidateType.RELAY || remote == IceCandidateType.RELAY) return RELAY;
            if (local == IceCandidateType.SRFLX || local == IceCandidateType.PRFLX
                    || remote == IceCandidateType.SRFLX || remote == IceCandidateType.PRFLX) return DIRECT;
            return (local == IceCandidateType.HOST && remote == IceCandidateType.HOST) ? LOCAL : UNKNOWN;
        }
    }

    public static final class State {
        private IceCandidateType localCandidateType;
        private IceCandidateType remoteCandidateType;
        private FailureStage failureStage;

        public synchronized Snapshot snapshot() {
            return new Snapshot(localCandidateType, remoteCandidateType, failureStage);
        }
        public synchronized void setIceInfo(IceCandidateType local, IceCandidateType remote) {
            this.localCandidateType = local;
            this.remoteCandidateType = remote;
        }
        public synchronized void setFailureStage(FailureStage fs) {
            if (this.failureStage == null) this.failureStage = fs;
        }

        public static final class Snapshot {
            private final IceCandidateType localCandidateType;
            private final IceCandidateType remoteCandidateType;
            private final FailureStage failureStage;
            public Snapshot(IceCandidateType l, IceCandidateType r, FailureStage f) {
                this.localCandidateType = l; this.remoteCandidateType = r; this.failureStage = f;
            }
            public IceCandidateType localCandidateType() { return localCandidateType; }
            public IceCandidateType remoteCandidateType() { return remoteCandidateType; }
            public FailureStage failureStage() { return failureStage; }
        }
    }
}
