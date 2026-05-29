package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** Ported from 26.2 JsonRPCUtils (Java 8 compatible). */
public final class JsonRPCUtils {
    public static final String JSONRPC = "2.0";

    public static JsonObject createRequest(Integer id, String method, List<JsonElement> params) {
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

    public static JsonElement getRequestId(JsonObject env) {
        return env.has("id") ? env.get("id") : null;
    }

    public static String getMethodName(JsonObject env) {
        if (env.has("method") && env.get("method").isJsonPrimitive()) return env.get("method").getAsString();
        return null;
    }

    public static JsonElement getResult(JsonObject env) {
        return env.has("result") ? env.get("result") : null;
    }

    public static JsonObject getError(JsonObject env) {
        return env.has("error") && env.get("error").isJsonObject() ? env.getAsJsonObject("error") : null;
    }

    public static JsonElement getParams(JsonObject env) {
        return env.has("params") ? env.get("params") : null;
    }

    private JsonRPCUtils() {}
}
