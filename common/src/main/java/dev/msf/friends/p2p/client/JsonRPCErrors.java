package dev.msf.friends.p2p.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Local clone of {@code net.minecraft.server.jsonrpc.JsonRPCErrors} — the
 * subset of JSON-RPC 2.0 errors the signaling client actually emits.
 *
 * <p>{@code create} / {@code createWithoutData} return a fully-formed
 * envelope ({@code jsonrpc=2.0, id, error{...}}) ready for sending.
 */
public enum JsonRPCErrors {
    PARSE_ERROR(-32700, "Parse error"),
    INVALID_REQUEST(-32600, "Invalid Request"),
    METHOD_NOT_FOUND(-32601, "Method not found"),
    INVALID_PARAMS(-32602, "Invalid params"),
    INTERNAL_ERROR(-32603, "Internal error");

    public final int code;
    public final String message;

    JsonRPCErrors(int code, String message) { this.code = code; this.message = message; }

    public JsonObject createWithoutData(JsonElement id) {
        JsonObject env = new JsonObject();
        env.addProperty("jsonrpc", "2.0");
        env.add("id", id);
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        env.add("error", err);
        return env;
    }

    public JsonObject create(JsonElement id, String data) {
        JsonObject env = createWithoutData(id);
        env.getAsJsonObject("error").add("data", new JsonPrimitive(data));
        return env;
    }
}
