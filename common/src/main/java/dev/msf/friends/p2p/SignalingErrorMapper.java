package dev.msf.friends.p2p;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.msf.friends.p2p.client.JsonRpcException;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Strict 26.2 port of {@code SignalingErrorMapper}. */
public final class SignalingErrorMapper {
    private static final String FIELD_CODE              = "Code";
    private static final String FIELD_MESSAGE           = "Message";
    private static final String DATA_MISSING_IDENTITY   = "MissingOrExpiredIdentity";
    private static final String DATA_UNKNOWN_PLAYER     = "UnknownPlayer";

    /** Numeric codes used inside service-pushed envelopes (see {@link #fromServiceEnvelope}). */
    private static final int CODE_PLAYER_UNREACHABLE     = 1;
    private static final int CODE_MESSAGE_DELIVERY_FAILED= 2;
    private static final int CODE_TURN_AUTH_FAILED       = 3;

    private SignalingErrorMapper() {}

    /**
     * Maps a JSON-RPC error returned for an outbound request (e.g. SendClientMessage).
     * Dispatch is by the {@code data.Code} string; if absent, the server message text
     * is sniffed for the legacy "not registered" phrase.
     */
    public static SignalingException fromJsonRpc(@Nullable UUID peerPmid, JsonRpcException err) {
        String dataCode = err.dataCode();
        String msg = serviceMessage(err);
        if (dataCode != null) {
            return switch (dataCode) {
                case DATA_MISSING_IDENTITY -> new SignalingException.SignalingAuthException(msg);
                case DATA_UNKNOWN_PLAYER   -> new SignalingException.UnknownPlayerException(peerPmid, msg);
                default -> new SignalingException.SignalingRejectedException(peerPmid, msg);
            };
        }
        return msg.contains("not registered")
                ? new SignalingException.UnknownPlayerException(peerPmid, msg)
                : new SignalingException.SignalingRejectedException(peerPmid, msg);
    }

    /**
     * Maps a service envelope inlined inside a {@code Signaling_ReceiveMessage_v1_0}
     * payload. {@code null} means "not an error envelope; treat as a normal message".
     */
    @Nullable
    public static SignalingException fromServiceEnvelope(@Nullable JsonElement body) {
        if (body == null || !body.isJsonObject()) return null;
        JsonObject obj = body.getAsJsonObject();
        if (!obj.has(FIELD_CODE) || !obj.get(FIELD_CODE).isJsonPrimitive()) return null;
        int code = obj.get(FIELD_CODE).getAsInt();
        String msg = serviceEnvelopeMessage(obj);
        return switch (code) {
            case CODE_PLAYER_UNREACHABLE      -> new SignalingException.UnknownPlayerException(null, msg);
            case CODE_MESSAGE_DELIVERY_FAILED -> new SignalingException.MessageUndeliveredException(msg);
            case CODE_TURN_AUTH_FAILED        -> new SignalingException.TurnAuthFailedException(msg);
            default -> new SignalingException.SignalingRejectedException(null, msg);
        };
    }

    private static String serviceMessage(JsonRpcException err) {
        JsonElement data = err.data();
        if (data != null && data.isJsonObject()) {
            String dataMessage = serviceEnvelopeMessage(data.getAsJsonObject());
            if (!dataMessage.isBlank()) return dataMessage;
        }
        return err.serverMessage();
    }

    private static String serviceEnvelopeMessage(JsonObject obj) {
        JsonElement message = obj.get(FIELD_MESSAGE);
        return (message != null && message.isJsonPrimitive()) ? message.getAsString() : "";
    }
}
