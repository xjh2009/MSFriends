package dev.msf.friends.p2p;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.msf.friends.p2p.client.JsonRpcException;

import java.util.UUID;

/**
 * Maps JSON-RPC errors and service envelopes to SignalingException subtypes.
 */
public final class SignalingErrorMapper {
    private SignalingErrorMapper() {}

    public static SignalingException fromJsonRpc(UUID peerPmid, JsonRpcException rpc) {
        int code = rpc.code();
        String msg = rpc.serverMessage();
        switch (code) {
            case -32001: return new SignalingException.MessageUndeliverable(msg, peerPmid);
            case -32002: return new SignalingException.SignalingAuth(msg);
            case -32003: return new SignalingException.SignalingRejected(msg, peerPmid);
            case -32004: return new SignalingException.UnknownPlayer(msg, peerPmid);
            case -32005: return new SignalingException.TurnAuthFailedException(msg);
            default:     return new SignalingException.MessageUndeliverable("RPC error " + code + ": " + msg, peerPmid);
        }
    }

    public static SignalingException fromServiceEnvelope(JsonElement envelope) {
        if (envelope == null || !envelope.isJsonObject()) return null;
        JsonObject obj = envelope.getAsJsonObject();
        if (!obj.has("error")) return null;
        JsonObject err = obj.getAsJsonObject("error");
        String msg = err.has("message") ? err.get("message").getAsString() : "Unknown service error";
        UUID pmid = null;
        if (err.has("pmid")) {
            try { pmid = UUID.fromString(err.get("pmid").getAsString()); } catch (Exception ignored) {}
        }
        int code = err.has("code") ? err.get("code").getAsInt() : -1;
        switch (code) {
            case -32001: return new SignalingException.MessageUndeliverable(msg, pmid);
            case -32002: return new SignalingException.SignalingAuth(msg);
            case -32003: return new SignalingException.SignalingRejected(msg, pmid);
            case -32004: return new SignalingException.UnknownPlayer(msg, pmid);
            default:     return new SignalingException.MessageUndeliverable(msg, pmid);
        }
    }
}
