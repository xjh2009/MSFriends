package dev.msf.friends.p2p.client;

import com.google.gson.JsonObject;

/**
 * JSON-RPC utilities.
 */
public final class JsonRPCUtils {
    private JsonRPCUtils() {}

    public static JsonObject createParams(String key, Object value) {
        JsonObject params = new JsonObject();
        if (value instanceof String) {
            params.addProperty(key, (String) value);
        } else if (value instanceof Number) {
            params.addProperty(key, (Number) value);
        } else if (value instanceof Boolean) {
            params.addProperty(key, (Boolean) value);
        } else {
            params.addProperty(key, value.toString());
        }
        return params;
    }
}
