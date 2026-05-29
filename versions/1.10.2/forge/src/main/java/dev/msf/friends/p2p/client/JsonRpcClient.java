package dev.msf.friends.p2p.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import dev.msf.friends.util.Logging;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JSON-RPC 2.0 WebSocket client, ported from 26.2 but using Netty WebSocket
 * instead of Java 11's java.net.http.WebSocket.
 */
public final class JsonRpcClient {
    private static final Logger LOGGER = Logging.get();
    private static final int MAX_MESSAGE_BYTES = 65536;

    public interface MethodHandler {
        void onMethod(JsonRpcClient rpc, JsonElement id, String method, JsonElement params);
    }

    private final ScheduledExecutorService executor;
    private final MethodHandler methodHandler;
    private final Runnable onDisconnect;

    private final Map<Integer, CompletableFuture<JsonElement>> pendingRequests = new HashMap<>();
    private final StringBuilder messageBuffer = new StringBuilder();
    private volatile Channel channel;
    private volatile boolean closed;
    private int transactionId;

    public JsonRpcClient(ScheduledExecutorService executor, MethodHandler methodHandler, Runnable onDisconnect) {
        this.executor = executor;
        this.methodHandler = methodHandler;
        this.onDisconnect = onDisconnect;
    }

    /**
     * Connect to the given WebSocket URI. Headers should contain auth info.
     * Returns a CompletableFuture that completes when the handshake succeeds.
     */
    public CompletableFuture<JsonRpcClient> connect(URI uri, Map<String, String> headers) {
        CompletableFuture<JsonRpcClient> future = new CompletableFuture<>();
        EventLoopGroup group = new NioEventLoopGroup(1, r -> {
            Thread t = new Thread(r, "WS-JsonRpc");
            t.setDaemon(true);
            return t;
        });

        DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            httpHeaders.set(e.getKey(), e.getValue());
        }

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, false, httpHeaders);

        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 ChannelPipeline p = ch.pipeline();
                 p.addLast(new HttpClientCodec());
                 p.addLast(new HttpObjectAggregator(8192));
                 p.addLast(new JsonRpcWebSocketHandler(handshaker, future, JsonRpcClient.this, group));
             }
         });

        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = "wss".equals(uri.getScheme()) ? 443 : 80;
        }

        ChannelFuture f = b.connect(host, port);
        f.addListener(cf -> {
            if (!cf.isSuccess()) {
                future.completeExceptionally(new IOException("WebSocket connect failed", cf.cause()));
                group.shutdownGracefully();
            }
        });
        return future;
    }

    // Called by the Netty handler
    void onChannelOpen(Channel ch) {
        this.channel = ch;
    }

    void onTextMessage(String text) {
        executor.execute(() -> {
            if (closed) return;
            try { dispatch(text); }
            catch (RuntimeException e) { LOGGER.error("[rpc] Failed to handle JSON-RPC message: {}", text, e); }
        });
    }

    void onWebSocketClosed() {
        executor.execute(() -> teardown(new IOException("Signaling WebSocket closed"), true));
    }

    void onWebSocketError(Throwable cause) {
        executor.execute(() -> teardown(new IOException("Signaling WebSocket errored", cause), true));
    }

    // -------- public API --------

    public void sendNotification(String method) {
        executor.execute(() -> send(JsonRPCUtils.createRequest(null, method, Collections.<com.google.gson.JsonElement>emptyList()).toString()));
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
            Channel ch = this.channel;
            if (ch == null || !ch.isActive()) {
                future.completeExceptionally(new IOException("WebSocket is not connected"));
                return;
            }
            int id = ++this.transactionId;
            String payload = JsonRPCUtils.createRequest(id, method, params).toString();
            this.pendingRequests.put(id, future);
            ch.writeAndFlush(new TextWebSocketFrame(payload)).addListener(f -> {
                if (!f.isSuccess()) {
                    executor.execute(() -> {
                        CompletableFuture<JsonElement> pending = pendingRequests.remove(id);
                        if (pending != null) pending.completeExceptionally(new IOException("WebSocket send failed", f.cause()));
                    });
                }
            });
        });
        return future;
    }

    public CompletableFuture<?> close() {
        CompletableFuture<Object> done = new CompletableFuture<>();
        executor.execute(() -> {
            Channel ch = this.channel;
            teardown(new IOException("JSON-RPC client closed"), false);
            if (ch != null && ch.isActive()) {
                ch.writeAndFlush(new CloseWebSocketFrame(1000, "shutdown")).addListener(f -> {
                    ch.close().addListener(g -> done.complete(null));
                });
            } else {
                done.complete(null);
            }
        });
        return done;
    }

    // -------- internals --------

    private void send(String payload) {
        Channel ch = this.channel;
        if (ch == null || !ch.isActive()) return;
        ch.writeAndFlush(new TextWebSocketFrame(payload)).addListener(f -> {
            if (!f.isSuccess()) LOGGER.warn("[rpc] WebSocket send failed", f.cause());
        });
    }

    private static boolean isValidResponseId(JsonElement id) {
        return id instanceof JsonPrimitive && ((JsonPrimitive) id).isNumber();
    }

    private void dispatch(String text) {
        JsonObject obj;
        try {
            JsonElement root = new JsonParser().parse(text);
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

    private void handleErrorResponse(JsonElement id, JsonObject error) {
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

    private void invokeHandler(JsonElement id, String method, JsonElement params) {
        try {
            methodHandler.onMethod(this, id, method, params);
        } catch (RuntimeException e) {
            LOGGER.error("[rpc] Handler threw for method {}", method, e);
            if (id != null) sendError(id, JsonRPCErrors.INTERNAL_ERROR);
        }
    }

    private void teardown(Throwable cause, boolean fireDisconnect) {
        closed = true;
        pendingRequests.values().forEach(p -> p.completeExceptionally(cause));
        pendingRequests.clear();
        if (fireDisconnect) {
            try { onDisconnect.run(); }
            catch (RuntimeException e) { LOGGER.error("[rpc] onDisconnect callback threw", e); }
        }
    }

    /**
     * Netty handler that bridges WebSocket events to JsonRpcClient.
     */
    private static final class JsonRpcWebSocketHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private final CompletableFuture<JsonRpcClient> connectFuture;
        private final JsonRpcClient rpcClient;
        private final EventLoopGroup group;

        JsonRpcWebSocketHandler(WebSocketClientHandshaker handshaker,
                                 CompletableFuture<JsonRpcClient> connectFuture,
                                 JsonRpcClient rpcClient,
                                 EventLoopGroup group) {
            this.handshaker = handshaker;
            this.connectFuture = connectFuture;
            this.rpcClient = rpcClient;
            this.group = group;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            handshaker.handshake(ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                rpcClient.onChannelOpen(ch);
                connectFuture.complete(rpcClient);
                return;
            }

            if (msg instanceof FullHttpResponse) {
                FullHttpResponse resp = (FullHttpResponse) msg;
                LOGGER.warn("[rpc] Unexpected HTTP response: {}", resp.getStatus());
                return;
            }

            if (msg instanceof TextWebSocketFrame) {
                String text = ((TextWebSocketFrame) msg).text();
                rpcClient.onTextMessage(text);
            } else if (msg instanceof CloseWebSocketFrame) {
                ch.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            rpcClient.onWebSocketClosed();
            group.shutdownGracefully();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (!connectFuture.isDone()) {
                connectFuture.completeExceptionally(cause);
            }
            rpcClient.onWebSocketError(cause);
            ctx.close();
        }
    }
}
