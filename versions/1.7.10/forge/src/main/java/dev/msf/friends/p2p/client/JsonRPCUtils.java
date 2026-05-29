package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

public final class JsonRPCUtils {
    private JsonRPCUtils() {}

    public static JsonObject createRequest(int id, String method, List<JsonElement> params) {
        JsonObject req = new JsonObject();
        req.addProperty("jsonrpc", "2.0");
        req.addProperty("id", id);
        req.addProperty("method", method);
        JsonArray arr = new JsonArray();
        if (params != null) {
            for (JsonElement p : params) arr.add(p);
        }
        req.add("params", arr);
        return req;
    }

    public static JsonObject createNotification(String method, List<JsonElement> params) {
        JsonObject req = new JsonObject();
        req.addProperty("jsonrpc", "2.0");
        req.addProperty("method", method);
        JsonArray arr = new JsonArray();
        if (params != null) {
            for (JsonElement p : params) arr.add(p);
        }
        req.add("params", arr);
        return req;
    }

    public static JsonObject createResponse(JsonElement id, JsonElement result) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id);
        resp.add("result", result);
        return resp;
    }

    public static JsonObject createError(JsonElement id, int code, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id);
        resp.add("error", err);
        return resp;
    }
}
