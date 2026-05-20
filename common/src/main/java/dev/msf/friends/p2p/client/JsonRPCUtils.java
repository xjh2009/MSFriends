package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Local clone of {@code net.minecraft.server.jsonrpc.JsonRPCUtils}.
 *
 * <p>All envelopes are JSON-RPC 2.0; the helper functions here mirror the
 * subset of behaviour the signaling client relies on (notification when
 * {@code id == null}, request when {@code id} is a number).
 */
public final class JsonRPCUtils {
    public static final String JSONRPC = "2.0";

    public static JsonObject createRequest(@Nullable Integer id, String method, List<JsonElement> params) {
        JsonObject env = new JsonObject();
        env.addProperty("jsonrpc", JSONRPC);
        if (id != null) env.add("id", new JsonPrimitive(id));
        env.addProperty("method", method);
        JsonArray arr = new JsonArray();
        for (JsonElement p : params) arr.add(p == null ? JsonNull.INSTANCE : p);
        env.add("params", arr);
        return env;
    }

    public static JsonObject createSuccessResult(JsonElement id, JsonElement result) {
        JsonObject env = new JsonObject();
        env.addProperty("jsonrpc", JSONRPC);
        env.add("id", id);
        env.add("result", result == null ? JsonNull.INSTANCE : result);
        return env;
    }

    @Nullable
    public static JsonElement getRequestId(JsonObject env) {
        return env.has("id") ? env.get("id") : null;
    }

    @Nullable
    public static String getMethodName(JsonObject env) {
        if (env.has("method") && env.get("method").isJsonPrimitive()) return env.get("method").getAsString();
        return null;
    }

    @Nullable
    public static JsonElement getResult(JsonObject env) {
        return env.has("result") ? env.get("result") : null;
    }

    @Nullable
    public static JsonObject getError(JsonObject env) {
        return env.has("error") && env.get("error").isJsonObject() ? env.getAsJsonObject("error") : null;
    }

    @Nullable
    public static JsonElement getParams(JsonObject env) {
        return env.has("params") ? env.get("params") : null;
    }

    private JsonRPCUtils() {}
}
