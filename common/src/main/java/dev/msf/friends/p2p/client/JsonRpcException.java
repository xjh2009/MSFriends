package dev.msf.friends.p2p.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/** Strict 26.2 port of {@code net.minecraft.client.multiplayer.p2p.client.JsonRpcException}. */
public final class JsonRpcException extends RuntimeException {
    private final int code;
    private final String serverMessage;
    private final @Nullable JsonElement data;

    public JsonRpcException(int code, String message, @Nullable JsonElement data) {
        super("JSON-RPC error " + code + ": " + message);
        this.code = code;
        this.serverMessage = message;
        this.data = data;
    }

    public int code() { return code; }

    @Nullable
    public JsonElement data() { return data; }

    public String serverMessage() { return serverMessage; }

    /** Returns the {@code Code} field of the {@code data} object, if any. Mirrors 26.2. */
    @Nullable
    public String dataCode() {
        if (data == null || !data.isJsonObject()) return null;
        JsonObject o = data.getAsJsonObject();
        JsonElement c = o.get("Code");
        return (c != null && c.isJsonPrimitive()) ? c.getAsString() : null;
    }
}
