package dev.msf.friends.p2p.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class JsonRpcException extends RuntimeException {
    private final int code;
    private final String serverMessage;
    private final JsonElement data;

    public JsonRpcException(int code, String message, JsonElement data) {
        super("JSON-RPC error " + code + ": " + message);
        this.code = code;
        this.serverMessage = message;
        this.data = data;
    }

    public int code() { return code; }
    public JsonElement data() { return data; }
    public String serverMessage() { return serverMessage; }

    public String dataCode() {
        if (data == null || !data.isJsonObject()) return null;
        JsonObject o = data.getAsJsonObject();
        JsonElement c = o.get("Code");
        return (c != null && c.isJsonPrimitive()) ? c.getAsString() : null;
    }
}
