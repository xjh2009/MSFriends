package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import dev.msf.friends.MsfFriendsConstants;
import dev.msf.friends.p2p.SignalingErrorMapper;
import dev.msf.friends.p2p.SignalingException;
import dev.msf.friends.p2p.SignalingMessage;
import dev.msf.friends.util.Logging;
import dev.onvoid.webrtc.RTCIceServer;
import org.apache.logging.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket JSON-RPC client for Mojang signaling service (1.7.10 / Java 8).
 * Uses org.java_websocket.client.WebSocketClient since java.net.http doesn't exist.
 */
public final class SignalingServiceClient {
    private static final Logger LOGGER = Logging.get(SignalingServiceClient.class);
    private static final String WS_CONNECTION_ENDPOINT = "/ws/v1.0/messaging/connect/java";
    private static final long PING_INTERVAL_MS = 50000L;
    private static final String HEADER_AUTH = "x-mojangauth";
    private static final String HEADER_SESSION_ID = "Session-Id";
    private static final String HEADER_REQUEST_ID = "Request-Id";

    public enum Environment {
        STAGE("https://signaling-afd.stage-6fd5f759.franchise.minecraft-services.net"),
        PRODUCTION("https://signaling-afd.franchise.minecraft-services.net");

        private static final String CONFIGURATION_ENDPOINT = "/api/v1.0/configuration/java";
        public final String baseUrl;
        Environment(String baseUrl) { this.baseUrl = baseUrl; }
        public String getConfigurationUri() { return baseUrl + CONFIGURATION_ENDPOINT; }

        public static Environment byName(String name) {
            if (name == null) return PRODUCTION;
            switch (name.toLowerCase(Locale.ROOT)) {
                case "stage": case "staging": return STAGE;
                default: return PRODUCTION;
            }
        }
    }

    public interface ConnectionListener {
        default void onSignalingError(UUID peerPmid, SignalingException cause) {}
        default void onSignalingDisconnected() {}
    }

    public interface UserCredentials {
        String accessToken();
        UUID profileId();
        String userName();
    }

    private static final Environment ENVIRONMENT;
    static {
        String envProp = System.getProperty(MsfFriendsConstants.SIGNALING_ENV_PROP);
        String envEnv = System.getenv("signaling.environment");
        ENVIRONMENT = Environment.byName(envEnv != null ? envEnv : envProp);
    }

    private final UserCredentials user;
    private final String sessionId = UUID.randomUUID().toString();
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "P2P-Signaling");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "P2P-Signaling-Ping");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger requestIdCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonElement>> pendingRequests = new ConcurrentHashMap<>();

    private volatile WebSocketClient wsClient;
    private volatile ScheduledFuture<?> pingTask;
    private volatile FriendJoinConsumer friendJoinHandler;
    private volatile WebRtcSignalingConsumer webRtcSignalingHandler;
    private volatile CachedTurn cachedTurn;
    private volatile CachedSignalingUri cachedSignalingUri;

    public SignalingServiceClient(UserCredentials user) {
        this.user = user;
    }

    @FunctionalInterface
    public interface FriendJoinConsumer {
        void accept(UUID fromPmid, SignalingMessage.FriendJoin message);
    }

    @FunctionalInterface
    public interface WebRtcSignalingConsumer {
        void accept(UUID fromPmid, SignalingMessage.WebRtc message);
    }

    public Environment environment() { return ENVIRONMENT; }
    public String sessionId() { return sessionId; }

    public void setFriendJoinHandler(FriendJoinConsumer h) {
        executor.execute(() -> this.friendJoinHandler = h);
    }

    public void setWebRtcSignalingHandler(WebRtcSignalingConsumer h) {
        executor.execute(() -> this.webRtcSignalingHandler = h);
    }

    public void addConnectionListener(ConnectionListener l) { connectionListeners.add(l); }
    public void removeConnectionListener(ConnectionListener l) { connectionListeners.remove(l); }

    public void connect() { executor.execute(this::connectWebSocket); }

    public void disconnect() { executor.execute(() -> teardown("explicit disconnect")); }

    public void clearHandlers() {
        executor.execute(() -> {
            this.friendJoinHandler = null;
            this.webRtcSignalingHandler = null;
        });
    }

    // -------- TURN --------
    public CompletableFuture<RTCIceServer> requestTurnAuth() {
        CompletableFuture<RTCIceServer> f = new CompletableFuture<>();
        executor.execute(() -> {
            CachedTurn cached = this.cachedTurn;
            if (cached != null && cached.isUsable()) {
                f.complete(cached.turnAuth.toRtcIceServer());
                return;
            }
            sendRequest(MsfFriendsConstants.RPC_TURN_AUTH, new ArrayList<JsonElement>())
                .thenAcceptAsync(result -> {
                    try {
                        TurnAuthResult turnAuth = TurnAuthResult.fromJson(result);
                        RTCIceServer ice = turnAuth.toRtcIceServer();
                        this.cachedTurn = new CachedTurn(turnAuth);
                        f.complete(ice);
                    } catch (Exception e) {
                        f.completeExceptionally(new SignalingException.TurnAuthFailedException(e.getMessage()));
                    }
                }, executor)
                .exceptionally(err -> {
                    f.completeExceptionally(err);
                    return null;
                });
        });
        return f;
    }

    // -------- send a client-to-client message --------
    public CompletableFuture<Void> sendClientMessage(UUID toPlayerId, SignalingMessage message) {
        String encoded = message.toJson().toString();
        return sendRequest(MsfFriendsConstants.RPC_SEND_CLIENT, java.util.Arrays.<JsonElement>asList(
                JsonNull.INSTANCE,
                new JsonPrimitive(toPlayerId.toString()),
                new JsonPrimitive(encoded)))
            .thenApply(v -> (Void) null)
            .exceptionally(err -> {
                Throwable cause = err.getCause() != null ? err.getCause() : err;
                if (cause instanceof JsonRpcException) {
                    JsonRpcException rpcErr = (JsonRpcException) cause;
                    SignalingException mapped = SignalingErrorMapper.fromJsonRpc(toPlayerId, rpcErr);
                    LOGGER.warn("Signaling rejected send: {}", mapped.getMessage());
                    fireOnSignalingError(toPlayerId, mapped);
                    throw new RuntimeException(mapped);
                }
                throw new RuntimeException(cause);
            });
    }

    // -------- websocket lifecycle --------
    private void connectWebSocket() {
        if (wsClient != null) return;
        try {
            String wsUrl = getSignalingUri();
            if (wsUrl == null) {
                LOGGER.warn("Failed to get signaling URI");
                return;
            }
            Map<String, String> headers = new HashMap<>();
            headers.put(HEADER_AUTH, user.accessToken());
            headers.put(HEADER_SESSION_ID, sessionId);
            headers.put(HEADER_REQUEST_ID, UUID.randomUUID().toString());

            URI uri = URI.create(wsUrl);
            WebSocketClient client = new WebSocketClient(uri, headers) {
                @Override public void onOpen(ServerHandshake handshake) {
                    LOGGER.info("[Signaling] WebSocket connected");
                    schedulePing();
                }
                @Override public void onMessage(String message) {
                    handleMessage(message);
                }
                @Override public void onClose(int code, String reason, boolean remote) {
                    LOGGER.info("[Signaling] WebSocket closed: {} {} remote={}", code, reason, remote);
                    if (remote) teardown("remote closed: " + reason);
                }
                @Override public void onError(Exception ex) {
                    LOGGER.error("[Signaling] WebSocket error", ex);
                }
            };
            this.wsClient = client;
            client.connect();
        } catch (Exception e) {
            LOGGER.error("[Signaling] Failed to connect", e);
            teardown("connect failed: " + e.getMessage());
        }
    }

    private void handleMessage(String raw) {
        try {
            JsonObject msg = new JsonParser().parse(raw).getAsJsonObject();
            String method = msg.has("method") ? msg.get("method").getAsString() : null;
            JsonElement id = msg.get("id");

            if (method != null) {
                // It's a request or notification from the server
                JsonElement params = msg.get("params");
                onRpcMethod(id, method, params);
            } else if (id != null) {
                // It's a response to one of our requests
                int idInt = id.getAsInt();
                CompletableFuture<JsonElement> pending = pendingRequests.remove(idInt);
                if (pending != null) {
                    if (msg.has("error")) {
                        JsonObject err = msg.getAsJsonObject("error");
                        int code = err.get("code").getAsInt();
                        String errMsg = err.get("message").getAsString();
                        String data = err.has("data") ? err.get("data").toString() : null;
                        pending.completeExceptionally(new JsonRpcException(code, errMsg, data));
                    } else {
                        pending.complete(msg.get("result"));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Signaling] Failed to parse message: {}", raw, e);
        }
    }

    private void onRpcMethod(JsonElement id, String method, JsonElement params) {
        switch (method) {
            case MsfFriendsConstants.RPC_PONG: break;
            case MsfFriendsConstants.RPC_RECEIVE: handleReceiveMessage(id, params); break;
            default:
                if (id != null) {
                    JsonObject err = JsonRPCUtils.createError(id, JsonRPCErrors.METHOD_NOT_FOUND, method);
                    sendRaw(err.toString());
                }
        }
    }

    private void handleReceiveMessage(JsonElement id, JsonElement params) {
        JsonArray arr = (params != null && params.isJsonArray()) ? params.getAsJsonArray() : null;
        JsonElement first = (arr != null && arr.size() > 0) ? arr.get(0) : null;
        if (first == null || !first.isJsonObject()) {
            LOGGER.warn("Malformed ReceiveMessage params: {}", params);
            return;
        }
        if (id != null) sendRaw(JsonRPCUtils.createResponse(id, new JsonObject()).toString());

        JsonObject envelope = first.getAsJsonObject();
        String from = envelope.get("From").getAsString();
        String messageStr = envelope.get("Message").getAsString();

        JsonElement inner;
        try { inner = new JsonParser().parse(messageStr); }
        catch (JsonSyntaxException e) { return; }

        SignalingException serviceError = SignalingErrorMapper.fromServiceEnvelope(inner);
        if (serviceError != null) {
            UUID errorPmid = serviceError.peerPmid() != null ? serviceError.peerPmid() : parseUuid(from);
            fireOnSignalingError(errorPmid, serviceError);
            return;
        }

        SignalingMessage parsed;
        try { parsed = SignalingMessage.fromJson(inner.getAsJsonObject()); }
        catch (Exception e) { return; }

        UUID fromPmid = parseUuid(from);
        if (parsed instanceof SignalingMessage.FriendJoin) {
            FriendJoinConsumer h = this.friendJoinHandler;
            if (h != null) {
                try { h.accept(fromPmid, (SignalingMessage.FriendJoin) parsed); }
                catch (Exception e) { LOGGER.error("Failed to dispatch FriendJoin", e); }
            }
        } else if (parsed instanceof SignalingMessage.WebRtc) {
            WebRtcSignalingConsumer h = this.webRtcSignalingHandler;
            if (h != null) {
                try { h.accept(fromPmid, (SignalingMessage.WebRtc) parsed); }
                catch (Exception e) { LOGGER.error("Failed to dispatch WebRtc", e); }
            }
        }
    }

    private void schedulePing() {
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                JsonObject ping = JsonRPCUtils.createNotification(MsfFriendsConstants.RPC_PING, new ArrayList<JsonElement>());
                sendRaw(ping.toString());
            } catch (Exception e) {
                LOGGER.warn("Signaling ping failed", e);
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private boolean teardown(String reason) {
        WebSocketClient client = this.wsClient;
        if (client == null) return false;
        LOGGER.debug("Signaling disconnecting ({})", reason);
        if (pingTask != null) { pingTask.cancel(false); pingTask = null; }
        try { client.close(); } catch (Exception ignored) {}
        this.wsClient = null;
        for (CompletableFuture<JsonElement> f : pendingRequests.values()) {
            f.completeExceptionally(new CancellationException("Signaling torn down"));
        }
        pendingRequests.clear();
        return true;
    }

    private CompletableFuture<JsonElement> sendRequest(String method, List<JsonElement> params) {
        int id = requestIdCounter.incrementAndGet();
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        JsonObject req = JsonRPCUtils.createRequest(id, method, params);
        sendRaw(req.toString());
        return future;
    }

    private void sendRaw(String text) {
        WebSocketClient client = this.wsClient;
        if (client != null && client.isOpen()) {
            client.send(text);
        }
    }

    private String getSignalingUri() {
        CachedSignalingUri cached = cachedSignalingUri;
        if (cached != null && cached.isUsable()) return cached.wsUrl;

        try {
            URL url = new URL(ENVIRONMENT.getConfigurationUri());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty(HEADER_AUTH, user.accessToken());
            conn.setRequestProperty(HEADER_SESSION_ID, sessionId);
            conn.setRequestProperty(HEADER_REQUEST_ID, UUID.randomUUID().toString());
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            if (code != 200) {
                LOGGER.warn("Signaling config returned {}", code);
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JsonObject root = new JsonParser().parse(sb.toString()).getAsJsonObject();
            String baseUri = root.getAsJsonObject("result").get("signalingUri").getAsString();
            String wsUrl = baseUri + WS_CONNECTION_ENDPOINT;
            cachedSignalingUri = new CachedSignalingUri(wsUrl);
            return wsUrl;
        } catch (Exception e) {
            LOGGER.error("Failed to get signaling URI", e);
            return null;
        }
    }

    private void fireOnSignalingError(UUID pmid, SignalingException ex) {
        for (ConnectionListener l : connectionListeners) {
            try { l.onSignalingError(pmid, ex); } catch (Exception e) { LOGGER.error("Listener error", e); }
        }
    }

    private static UUID parseUuid(String s) {
        if (s.length() == 32 && !s.contains("-")) {
            s = s.substring(0,8) + "-" + s.substring(8,12) + "-" + s.substring(12,16) + "-" + s.substring(16,20) + "-" + s.substring(20);
        }
        return UUID.fromString(s);
    }

    // -------- Inner types --------
    private static class CachedSignalingUri {
        final String wsUrl;
        final long expiresAt;
        CachedSignalingUri(String wsUrl) { this.wsUrl = wsUrl; this.expiresAt = System.currentTimeMillis() + 300000L; }
        boolean isUsable() { return System.currentTimeMillis() < expiresAt; }
    }

    private static class CachedTurn {
        final TurnAuthResult turnAuth;
        final long expiresAt;
        CachedTurn(TurnAuthResult turnAuth) {
            this.turnAuth = turnAuth;
            this.expiresAt = System.currentTimeMillis() + (turnAuth.expirationInSeconds - 60) * 1000L;
        }
        boolean isUsable() { return System.currentTimeMillis() < expiresAt; }
    }

    private static class TurnAuthResult {
        final long expirationInSeconds;
        final List<TurnAuthServer> turnAuthServers;
        TurnAuthResult(long expirationInSeconds, List<TurnAuthServer> turnAuthServers) {
            this.expirationInSeconds = expirationInSeconds;
            this.turnAuthServers = turnAuthServers;
        }
        static TurnAuthResult fromJson(JsonElement root) {
            JsonObject o = root.getAsJsonObject();
            long exp = o.get("ExpirationInSeconds").getAsLong();
            List<TurnAuthServer> servers = new ArrayList<>();
            for (JsonElement el : o.getAsJsonArray("TurnAuthServers")) {
                servers.add(TurnAuthServer.fromJson(el.getAsJsonObject()));
            }
            return new TurnAuthResult(exp, servers);
        }
        RTCIceServer toRtcIceServer() {
            TurnAuthServer first = turnAuthServers.get(0);
            RTCIceServer ice = new RTCIceServer();
            ice.username = first.username;
            ice.password = first.password;
            for (TurnAuthServer s : turnAuthServers) ice.urls.addAll(s.urls);
            return ice;
        }
    }

    private static class TurnAuthServer {
        final String username;
        final String password;
        final List<String> urls;
        TurnAuthServer(String username, String password, List<String> urls) {
            this.username = username;
            this.password = password;
            this.urls = urls;
        }
        static TurnAuthServer fromJson(JsonObject o) {
            String u = o.get("Username").getAsString();
            String p = o.get("Password").getAsString();
            List<String> us = new ArrayList<>();
            for (JsonElement el : o.getAsJsonArray("Urls")) us.add(el.getAsString());
            return new TurnAuthServer(u, p, us);
        }
    }

    public static UserCredentials staticCredentials(String accessToken, UUID profileId, String name) {
        return new UserCredentials() {
            @Override public String accessToken() { return accessToken; }
            @Override public UUID profileId() { return profileId; }
            @Override public String userName() { return name; }
        };
    }
}
