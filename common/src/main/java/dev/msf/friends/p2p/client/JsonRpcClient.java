package dev.msf.friends.p2p.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import dev.msf.friends.util.Logging;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Strict 26.2 port of {@code net.minecraft.client.multiplayer.p2p.client.JsonRpcClient}.
 *
 * <p>Every state mutation (webSocket handle, transactionId counter, pending
 * map, sendChain) is performed exclusively on the supplied executor; the
 * WebSocket listener callbacks just hop onto it.
 */
public final class JsonRpcClient implements WebSocket.Listener {
    private static final Logger LOGGER = Logging.get();
    private static final int MAX_MESSAGE_BYTES = 65536;

    @FunctionalInterface
    public interface MethodHandler {
        void onMethod(JsonRpcClient rpc, @Nullable JsonElement id, String method, @Nullable JsonElement params);
    }

    private final ScheduledExecutorService executor;
    private final MethodHandler methodHandler;
    private final Runnable onDisconnect;

    private final Map<Integer, CompletableFuture<JsonElement>> pendingRequests = new HashMap<>();
    private final StringBuilder messageBuffer = new StringBuilder();
    private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
    private @Nullable WebSocket webSocket;
    private int transactionId;

    public JsonRpcClient(ScheduledExecutorService executor, MethodHandler methodHandler, Runnable onDisconnect) {
        this.executor = executor;
        this.methodHandler = methodHandler;
        this.onDisconnect = onDisconnect;
    }

    // -------- WebSocket.Listener --------

    @Override public void onOpen(WebSocket webSocket) {
        LOGGER.debug("[rpc] WebSocket connection established");
        executor.execute(() -> { this.webSocket = webSocket; webSocket.request(1); });
    }

    @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        executor.execute(() -> teardown(new IOException(
                "Signaling WebSocket closed (code=" + statusCode + ", reason=" + reason + ")"), true));
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence chars, boolean last) {
        String slice = chars.toString();
        executor.execute(() -> { appendAndDispatch(slice, last); webSocket.request(1); });
        return CompletableFuture.completedFuture(null);
    }

    @Override public void onError(WebSocket webSocket, Throwable error) {
        executor.execute(() -> teardown(new IOException("Signaling WebSocket errored", error), true));
    }

    // -------- public API (all marshal to executor) --------

    public void sendNotification(String method) {
        executor.execute(() -> send(JsonRPCUtils.createRequest(null, method, List.of()).toString()));
    }

    public void sendResponse(JsonElement id, JsonElement result) {
        executor.execute(() -> send(JsonRPCUtils.createSuccessResult(id, result).toString()));
    }

    public void sendError(JsonElement id, JsonRPCErrors error) {
        executor.execute(() -> send(error.createWithoutData(id).toString()));
    }

    public void sendError(JsonElement id, JsonRPCErrors error, String data) {
        executor.execute(() -> send(error.create(id, data).toString()));
    }

    public CompletableFuture<JsonElement> sendRequest(String method, List<JsonElement> params) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        executor.execute(() -> {
            WebSocket ws = this.webSocket;
            if (ws == null) {
                future.completeExceptionally(new IOException("WebSocket is not connected"));
                return;
            }
            int id = ++this.transactionId;
            String payload = JsonRPCUtils.createRequest(id, method, params).toString();
            this.pendingRequests.put(id, future);
            this.sendChain = this.sendChain
                    .thenCompose(unused -> ws.sendText(payload, true).thenApply(w -> (Void) null))
                    .exceptionally(err -> {
                        LOGGER.warn("[rpc] WebSocket send failed", err);
                        executor.execute(() -> {
                            CompletableFuture<JsonElement> pending = pendingRequests.remove(id);
                            if (pending != null) pending.completeExceptionally(new IOException("WebSocket send failed", err));
                        });
                        return null;
                    });
        });
        return future;
    }

    public CompletableFuture<?> close() {
        CompletableFuture<Object> done = new CompletableFuture<>();
        executor.execute(() -> {
            WebSocket ws = this.webSocket;
            teardown(new IOException("JSON-RPC client closed"), false);
            if (ws != null && !ws.isOutputClosed()) {
                ws.sendClose(1000, "shutdown").whenComplete((u, t) -> done.complete(null));
            } else {
                done.complete(null);
            }
        });
        return done;
    }

    // -------- internals --------

    private void send(String payload) {
        WebSocket ws = this.webSocket;
        if (ws == null) return;
        this.sendChain = this.sendChain
                .thenCompose(unused -> ws.sendText(payload, true).thenApply(w -> (Void) null))
                .exceptionally(err -> { LOGGER.warn("[rpc] WebSocket send failed", err); return null; });
    }

    private static boolean isValidResponseId(JsonElement id) {
        return id instanceof JsonPrimitive p && p.isNumber();
    }

    private void appendAndDispatch(String slice, boolean last) {
        if (messageBuffer.length() + slice.length() > MAX_MESSAGE_BYTES) {
            LOGGER.warn("[rpc] JSON-RPC message exceeded {} bytes, dropping", MAX_MESSAGE_BYTES);
            messageBuffer.setLength(0);
            return;
        }
        messageBuffer.append(slice);
        if (last) {
            String full = messageBuffer.toString();
            messageBuffer.setLength(0);
            try { dispatch(full); }
            catch (RuntimeException e) { LOGGER.error("[rpc] Failed to handle JSON-RPC message: {}", full, e); }
        }
    }

    private void dispatch(String text) {
        JsonObject obj;
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) { LOGGER.warn("[rpc] Dropping non-object JSON-RPC message"); return; }
            obj = root.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            LOGGER.warn("[rpc] Dropping unparseable JSON-RPC message: {}", e.getMessage());
            return;
        }
        JsonElement id = JsonRPCUtils.getRequestId(obj);
        boolean hasId = id != null && !id.isJsonNull();
        String method = JsonRPCUtils.getMethodName(obj);
        JsonElement result = JsonRPCUtils.getResult(obj);
        JsonObject error  = JsonRPCUtils.getError(obj);

        if (method != null && result == null && error == null) {
            invokeHandler(hasId ? id : null, method, JsonRPCUtils.getParams(obj));
        } else if (method == null && error == null && result != null && hasId) {
            if (isValidResponseId(id)) {
                completePending(id.getAsInt(), result);
            } else {
                LOGGER.warn("[rpc] Ignoring JSON-RPC response with non-numeric id: {}", id);
            }
        } else if (method == null && result == null && error != null) {
            handleErrorResponse(hasId ? id : null, error);
        } else {
            LOGGER.warn("[rpc] Dropping invalid JSON-RPC envelope");
        }
    }

    private void completePending(int id, JsonElement result) {
        CompletableFuture<JsonElement> pending = pendingRequests.remove(id);
        if (pending != null) pending.complete(result);
        else LOGGER.warn("[rpc] Received result for unknown request id={}", id);
    }

    private void handleErrorResponse(@Nullable JsonElement id, JsonObject error) {
        int code = error.has("code") ? error.get("code").getAsInt() : 0;
        String message = error.has("message") ? error.get("message").getAsString() : "";
        JsonElement data = error.get("data");
        if (id == null) {
            LOGGER.error("[rpc] JSON-RPC error (no id): code={} message={}", code, message);
            return;
        }
        if (!isValidResponseId(id)) {
            LOGGER.warn("[rpc] Ignoring JSON-RPC error with non-numeric id: {}", id);
            return;
        }
        CompletableFuture<JsonElement> pending = pendingRequests.remove(id.getAsInt());
        if (pending != null) {
            pending.completeExceptionally(new JsonRpcException(code, message, data));
        } else {
            LOGGER.warn("[rpc] Received error for unknown request id={}: code={} message={}", id, code, message);
        }
    }

    private void invokeHandler(@Nullable JsonElement id, String method, @Nullable JsonElement params) {
        try {
            methodHandler.onMethod(this, id, method, params);
        } catch (RuntimeException e) {
            LOGGER.error("[rpc] Handler threw for method {}", method, e);
            if (id != null) sendError(id, JsonRPCErrors.INTERNAL_ERROR);
        }
    }

    private void teardown(Throwable cause, boolean fireDisconnect) {
        webSocket = null;
        pendingRequests.values().forEach(p -> p.completeExceptionally(cause));
        pendingRequests.clear();
        if (fireDisconnect) {
            try { onDisconnect.run(); }
            catch (RuntimeException e) { LOGGER.error("[rpc] onDisconnect callback threw", e); }
        }
    }
}
