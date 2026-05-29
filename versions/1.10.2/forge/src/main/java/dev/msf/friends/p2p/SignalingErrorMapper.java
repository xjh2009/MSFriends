package dev.msf.friends.p2p;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.msf.friends.p2p.client.JsonRpcException;

import java.util.UUID;

public final class SignalingErrorMapper {
    private static final String FIELD_CODE              = "Code";
    private static final String FIELD_MESSAGE           = "Message";
    private static final String DATA_MISSING_IDENTITY   = "MissingOrExpiredIdentity";
    private static final String DATA_UNKNOWN_PLAYER     = "UnknownPlayer";
    private static final int CODE_PLAYER_UNREACHABLE     = 1;
    private static final int CODE_MESSAGE_DELIVERY_FAILED= 2;
    private static final int CODE_TURN_AUTH_FAILED       = 3;

    private SignalingErrorMapper() {}

    public static SignalingException fromJsonRpc(UUID peerPmid, JsonRpcException err) {
        String dataCode = err.dataCode();
        String msg = serviceMessage(err);
        if (dataCode != null) {
            if (DATA_MISSING_IDENTITY.equals(dataCode)) {
                return new SignalingException.SignalingAuthException(msg);
            } else if (DATA_UNKNOWN_PLAYER.equals(dataCode)) {
                return new SignalingException.UnknownPlayerException(peerPmid, msg);
            } else {
                return new SignalingException.SignalingRejectedException(peerPmid, msg);
            }
        }
        return msg.contains("not registered")
                ? new SignalingException.UnknownPlayerException(peerPmid, msg)
                : new SignalingException.SignalingRejectedException(peerPmid, msg);
    }

    public static SignalingException fromServiceEnvelope(JsonElement body) {
        if (body == null || !body.isJsonObject()) return null;
        JsonObject obj = body.getAsJsonObject();
        if (!obj.has(FIELD_CODE) || !obj.get(FIELD_CODE).isJsonPrimitive()) return null;
        int code = obj.get(FIELD_CODE).getAsInt();
        String msg = serviceEnvelopeMessage(obj);
        switch (code) {
            case CODE_PLAYER_UNREACHABLE:      return new SignalingException.UnknownPlayerException(null, msg);
            case CODE_MESSAGE_DELIVERY_FAILED: return new SignalingException.MessageUndeliveredException(msg);
            case CODE_TURN_AUTH_FAILED:        return new SignalingException.TurnAuthFailedException(msg);
            default: return new SignalingException.SignalingRejectedException(null, msg);
        }
    }

    private static String serviceMessage(JsonRpcException err) {
        JsonElement data = err.data();
        if (data != null && data.isJsonObject()) {
            String dataMessage = serviceEnvelopeMessage(data.getAsJsonObject());
            if (dataMessage != null && !dataMessage.trim().isEmpty()) return dataMessage;
        }
        return err.serverMessage();
    }

    private static String serviceEnvelopeMessage(JsonObject obj) {
        JsonElement message = obj.get(FIELD_MESSAGE);
        return (message != null && message.isJsonPrimitive()) ? message.getAsString() : "";
    }
}
