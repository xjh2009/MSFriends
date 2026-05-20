package dev.msf.friends.p2p;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Strict 26.2 port of {@code SignalingException}. */
public abstract class SignalingException extends RuntimeException {
    @Nullable private final UUID peerPmid;

    protected SignalingException(@Nullable UUID peerPmid, String message) {
        super(message);
        this.peerPmid = peerPmid;
    }

    @Nullable
    public final UUID peerPmid() { return peerPmid; }

    public static final class MessageUndeliveredException extends SignalingException {
        public MessageUndeliveredException(String serverMessage) { super(null, serverMessage); }
    }

    public static final class SignalingAuthException extends SignalingException {
        public SignalingAuthException(String serverMessage) { super(null, serverMessage); }
    }

    public static class SignalingRejectedException extends SignalingException {
        public SignalingRejectedException(@Nullable UUID peerPmid, String message) { super(peerPmid, message); }
    }

    public static final class TurnAuthFailedException extends SignalingException {
        public TurnAuthFailedException(String serverMessage) { super(null, serverMessage); }
    }

    public static final class UnknownPlayerException extends SignalingException {
        public UnknownPlayerException(@Nullable UUID peerPmid, String serverMessage) { super(peerPmid, serverMessage); }
    }
}
