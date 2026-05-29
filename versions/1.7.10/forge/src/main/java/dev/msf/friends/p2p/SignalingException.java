package dev.msf.friends.p2p;

import java.util.UUID;

/**
 * Signaling exception hierarchy for 1.7.10 (Java 8 — no sealed classes).
 */
public abstract class SignalingException extends Exception {
    private final UUID peerPmid;

    protected SignalingException(String message, UUID peerPmid) {
        super(message);
        this.peerPmid = peerPmid;
    }

    protected SignalingException(String message, UUID peerPmid, Throwable cause) {
        super(message, cause);
        this.peerPmid = peerPmid;
    }

    public UUID peerPmid() { return peerPmid; }

    public static class MessageUndeliverable extends SignalingException {
        public MessageUndeliverable(String message, UUID peerPmid) { super(message, peerPmid); }
    }
    public static class SignalingAuth extends SignalingException {
        public SignalingAuth(String message) { super(message, null); }
    }
    public static class SignalingRejected extends SignalingException {
        public SignalingRejected(String message, UUID peerPmid) { super(message, peerPmid); }
    }
    public static class TurnAuthFailedException extends SignalingException {
        public TurnAuthFailedException(String message) { super(message, null); }
    }
    public static class UnknownPlayer extends SignalingException {
        public UnknownPlayer(String message, UUID peerPmid) { super(message, peerPmid); }
    }
}
