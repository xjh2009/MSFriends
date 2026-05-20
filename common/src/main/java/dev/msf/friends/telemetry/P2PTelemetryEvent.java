package dev.msf.friends.telemetry;

import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Strict 26.2 port of {@code net.minecraft.client.telemetry.events.P2PTelemetryEvent}.
 *
 * <p>The original sends to Mojang's {@code TelemetryManager.getOutsideSessionSender()};
 * here we just log the event payload. The field shape and {@link IcePath} classification
 * algorithm are preserved verbatim so a future hook into MC's telemetry can replace
 * the {@link #send} body without touching call-sites.
 */
public final class P2PTelemetryEvent {
    private static final Logger LOGGER = Logging.get();
    public static final P2PTelemetryEvent INSTANCE = new P2PTelemetryEvent();

    public void send(boolean successful,
                     State state,
                     @Nullable Instant connectionStartTime,
                     @Nullable Instant signalingDoneTime,
                     @Nullable Instant connectionEstablishedTime) {
        State.Snapshot s = state.snapshot();
        Long totalMs        = millisBetween(connectionStartTime, connectionEstablishedTime);
        Long signalingMs    = millisBetween(connectionStartTime, signalingDoneTime);
        Long iceConnectMs   = millisBetween(signalingDoneTime, connectionEstablishedTime);
        IcePath path = (s.localCandidateType() != null && s.remoteCandidateType() != null)
                ? IcePath.classify(s.localCandidateType(), s.remoteCandidateType())
                : null;
        LOGGER.info("[telemetry] P2P_CONNECTION successful={} icePath={} local={} remote={} totalMs={} signalingMs={} iceConnectMs={} failureStage={}",
                successful, path,
                s.localCandidateType(), s.remoteCandidateType(),
                totalMs, signalingMs, iceConnectMs,
                successful ? null : s.failureStage());
    }

    @Nullable
    private static Long millisBetween(@Nullable Instant from, @Nullable Instant to) {
        return (from != null && to != null) ? from.until(to, ChronoUnit.MILLIS) : null;
    }

    public enum FailureStage { SIGNALING, ICE_CONNECT, TIMEOUT;
        public String getSerializedName() { return name(); }
    }

    public enum IceCandidateType {
        HOST("host"), SRFLX("srflx"), PRFLX("prflx"), RELAY("relay");

        private static final Map<String, IceCandidateType> BY_NAME = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(IceCandidateType::getSerializedName, Function.identity()));
        private final String name;
        IceCandidateType(String name) { this.name = name; }
        public String getSerializedName() { return name; }
        public static Optional<IceCandidateType> byName(String name) { return Optional.ofNullable(BY_NAME.get(name)); }
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
        @Nullable private IceCandidateType localCandidateType;
        @Nullable private IceCandidateType remoteCandidateType;
        @Nullable private FailureStage failureStage;

        public synchronized Snapshot snapshot() {
            return new Snapshot(localCandidateType, remoteCandidateType, failureStage);
        }

        public synchronized void setIceInfo(IceCandidateType local, IceCandidateType remote) {
            this.localCandidateType = local;
            this.remoteCandidateType = remote;
        }

        public synchronized void setFailureStage(FailureStage failureStage) {
            if (this.failureStage == null) this.failureStage = failureStage;
        }

        public record Snapshot(@Nullable IceCandidateType localCandidateType,
                               @Nullable IceCandidateType remoteCandidateType,
                               @Nullable FailureStage failureStage) {}
    }
}
