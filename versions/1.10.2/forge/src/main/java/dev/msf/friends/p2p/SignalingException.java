package dev.msf.friends.p2p;

import java.util.UUID;

public abstract class SignalingException extends RuntimeException {
    private final UUID peerPmid;

    protected SignalingException(UUID peerPmid, String message) {
        super(message);
        this.peerPmid = peerPmid;
    }

    public final UUID peerPmid() { return peerPmid; }

    public static final class MessageUndeliveredException extends SignalingException {
        public MessageUndeliveredException(String serverMessage) { super(null, serverMessage); }
    }

    public static final class SignalingAuthException extends SignalingException {
        public SignalingAuthException(String serverMessage) { super(null, serverMessage); }
    }

    public static class SignalingRejectedException extends SignalingException {
        public SignalingRejectedException(UUID peerPmid, String message) { super(peerPmid, message); }
    }

    public static final class TurnAuthFailedException extends SignalingException {
        public TurnAuthFailedException(String serverMessage) { super(null, serverMessage); }
    }

    public static final class UnknownPlayerException extends SignalingException {
        public UnknownPlayerException(UUID peerPmid, String serverMessage) { super(peerPmid, serverMessage); }
    }
}
