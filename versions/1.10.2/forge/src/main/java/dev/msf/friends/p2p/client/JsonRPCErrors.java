package dev.msf.friends.p2p.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Ported from 26.2 JsonRPCErrors as an enum. */
public enum JsonRPCErrors {
    PARSE_ERROR(-32700, "Parse error"),
    INVALID_REQUEST(-32600, "Invalid Request"),
    METHOD_NOT_FOUND(-32601, "Method not found"),
    INVALID_PARAMS(-32602, "Invalid params"),
    INTERNAL_ERROR(-32603, "Internal error");

    private final int code;
    private final String message;

    JsonRPCErrors(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }

    public JsonObject createWithoutData(JsonElement id) {
        return createWithError(id, null);
    }

    public JsonObject create(JsonElement id, String data) {
        return createWithError(id, data);
    }

    private JsonObject createWithError(JsonElement id, String data) {
        JsonObject errObj = new JsonObject();
        errObj.addProperty("code", code);
        errObj.addProperty("message", message);
        if (data != null) errObj.addProperty("data", data);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        if (id != null) envelope.add("id", id);
        envelope.add("error", errObj);
        return envelope;
    }

    @Override
    public String toString() { return message; }
}
