package dev.msf.friends.p2p.client;

import com.google.gson.*;
import javax.annotation.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JsonRpcClient - Java 8 compatible version.
 * Sends JSON-RPC 2.0 requests over an abstract transport.
 */
public class JsonRpcClient {
    private static final Gson GSON = new GsonBuilder().create();
    private final AtomicLong idCounter = new AtomicLong(1);

    public interface Transport {
        CompletableFuture<String> sendRequest(String jsonRequest);
    }

    private final Transport transport;

    public JsonRpcClient(Transport transport) {
        this.transport = transport;
    }

    public CompletableFuture<JsonElement> call(String method, @Nullable JsonObject params) {
        long id = idCounter.getAndIncrement();
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", id);
        request.addProperty("method", method);
        if (params != null) request.add("params", params);

        return transport.sendRequest(request.toString())
                .thenApply(responseJson -> {
                    try {
                        JsonObject response = new JsonParser().parse(responseJson).getAsJsonObject();
                        if (response.has("error")) {
                            JsonObject error = response.getAsJsonObject("error");
                            int code = error.get("code").getAsInt();
                            String msg = error.has("message") ? error.get("message").getAsString() : "";
                            JsonElement data = error.has("data") ? error.get("data") : null;
                            String dataCode = null;
                            if (data != null && data.isJsonObject()) {
                                JsonObject dataObj = data.getAsJsonObject();
                                if (dataObj.has("code")) dataCode = dataObj.get("code").getAsString();
                            }
                            throw new JsonRpcException(code, msg, data, dataCode);
                        }
                        return response.has("result") ? response.get("result") : JsonNull.INSTANCE;
                    } catch (JsonRpcException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new JsonRpcException(JsonRPCErrors.PARSE_ERROR, "Failed to parse response: " + e.getMessage(), null, null);
                    }
                });
    }

    public CompletableFuture<JsonElement> notify(String method, @Nullable JsonObject params) {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", method);
        if (params != null) request.add("params", params);

        return transport.sendRequest(request.toString())
                .thenApply(responseJson -> {
                    try {
                        JsonObject response = new JsonParser().parse(responseJson).getAsJsonObject();
                        if (response.has("error")) {
                            JsonObject error = response.getAsJsonObject("error");
                            int code = error.get("code").getAsInt();
                            String msg = error.has("message") ? error.get("message").getAsString() : "";
                            throw new JsonRpcException(code, msg, null, null);
                        }
                        return response.has("result") ? response.get("result") : JsonNull.INSTANCE;
                    } catch (JsonRpcException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new JsonRpcException(JsonRPCErrors.PARSE_ERROR, "Failed to parse: " + e.getMessage(), null, null);
                    }
                });
    }
}
