package dev.msf.friends.p2p.client;

import com.google.gson.JsonElement;
import javax.annotation.Nullable;

/**
 * JsonRpcException - Java 8 compatible version.
 */
public class JsonRpcException extends RuntimeException {
    private final int code;
    private final String serverMessage;
    @Nullable private final JsonElement data;
    @Nullable private final String dataCode;

    public JsonRpcException(int code, String serverMessage, @Nullable JsonElement data, @Nullable String dataCode) {
        super("JSON-RPC error " + code + ": " + serverMessage);
        this.code = code;
        this.serverMessage = serverMessage;
        this.data = data;
        this.dataCode = dataCode;
    }

    public int code() { return code; }
    public String serverMessage() { return serverMessage; }
    @Nullable public JsonElement data() { return data; }
    @Nullable public String dataCode() { return dataCode; }
}
