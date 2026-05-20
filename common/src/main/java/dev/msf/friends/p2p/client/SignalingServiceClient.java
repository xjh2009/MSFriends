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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Strict 26.2 port of {@code SignalingServiceClient}.
 *
 * <p>Wire details preserved verbatim:
 * <ul>
 *   <li>{@code /api/v1.0/configuration/java} returns {@code {"result":{"signalingUri":"..."}}}.</li>
 *   <li>The wsUrl returned to the WebSocket builder is
 *       {@code &lt;baseUri&gt; + /ws/v1.0/messaging/connect/java}.</li>
 *   <li>{@code Signaling_SendClientMessage_v1_0} parameters:
 *       {@code [null, "&lt;toPlayerId UUID&gt;", "&lt;encoded SignalingMessage JSON&gt;"]}.</li>
 *   <li>{@code Signaling_ReceiveMessage_v1_0} parameters:
 *       {@code [{"From":"&lt;UUID&gt;","Message":"&lt;encoded JSON&gt;"[,"Id":"&lt;UUID&gt;"]}]}.</li>
 *   <li>{@code Signaling_TurnAuth_v1_0} returns
 *       {@code {ExpirationInSeconds, TurnAuthServers:[{Username,Password,Urls:[..]}]}}.</li>
 *   <li>50-second {@code System_Ping_v1_0} notification while open; {@code System_Pong_v1_0} arrives back.</li>
 * </ul>
 */
public final class SignalingServiceClient {
    private static final Logger LOGGER = Logging.get();
    private static final String WS_CONNECTION_ENDPOINT = "/ws/v1.0/messaging/connect/java";
    private static final Duration PING_INTERVAL = Duration.ofSeconds(50L);
    private static final String HEADER_AUTH = "x-mojangauth";
    private static final String HEADER_SESSION_ID = "Session-Id";
    private static final String HEADER_REQUEST_ID = "Request-Id";

    private static final String RPC_PING = "System_Ping_v1_0";
    private static final String RPC_PONG = "System_Pong_v1_0";
    private static final String RPC_TURN_AUTH    = "Signaling_TurnAuth_v1_0";
    private static final String RPC_SEND_CLIENT  = "Signaling_SendClientMessage_v1_0";
    private static final String RPC_RECEIVE      = "Signaling_ReceiveMessage_v1_0";

    public enum Environment {
        STAGE("https://signaling-afd.stage-6fd5f759.franchise.minecraft-services.net"),
        PRODUCTION("https://signaling-afd.franchise.minecraft-services.net");

        private static final String CONFIGURATION_ENDPOINT = "/api/v1.0/configuration/java";
        public final String baseUrl;
        Environment(String baseUrl) { this.baseUrl = baseUrl; }
        public String getConfigurationUri() { return baseUrl + CONFIGURATION_ENDPOINT; }
        public static Optional<Environment> byName(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "stage", "staging"  -> Optional.of(STAGE);
                case "prod", "production"-> Optional.of(PRODUCTION);
                default -> Optional.empty();
            };
        }
    }

    public interface ConnectionListener {
        default void onSignalingError(@Nullable UUID peerPmid, SignalingException cause) {}
        default void onSignalingDisconnected() {}
    }

    @FunctionalInterface
    public interface FriendJoinHandler {
        void handle(UUID fromPmid, SignalingMessage.FriendJoin message);
    }

    @FunctionalInterface
    public interface WebRtcSignalingHandler {
        void handle(UUID fromPmid, SignalingMessage.WebRtc message);
    }

    /** Pluggable user provider so the mod can inject the live MC user lazily. */
    public interface UserCredentials {
        String accessToken();
        UUID profileId();
        default String userName() { return ""; }
    }

    private static final Environment ENVIRONMENT = Optional.ofNullable(System.getenv("signaling.environment"))
            .or(() -> Optional.ofNullable(System.getProperty(MsfFriendsConstants.SIGNALING_ENV_PROP)))
            .flatMap(Environment::byName)
            .orElse(Environment.PRODUCTION);

    private final UserCredentials user;
    private final String sessionId = UUID.randomUUID().toString();
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "P2P-Signaling");
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((th, e) -> LOGGER.error("Uncaught in {}", th, e));
        return t;
    });
    @Nullable private HttpClient httpClient;
    @Nullable private CompletableFuture<JsonRpcClient> websocketConnect;
    @Nullable private ScheduledFuture<?> pingTask;
    @Nullable private FriendJoinHandler friendJoinHandler;
    @Nullable private WebRtcSignalingHandler webRtcSignalingHandler;
    @Nullable private CachedTurn cachedTurn;
    @Nullable private CachedSignalingUri cachedSignalingUri;
    @Nullable private CompletableFuture<RTCIceServer> pendingTurnRefresh;

    public SignalingServiceClient(UserCredentials user) {
        this.user = user;
    }

    public Environment environment() { return ENVIRONMENT; }
    public String sessionId() { return sessionId; }

    public void setFriendJoinHandler(@Nullable FriendJoinHandler h) { executor.execute(() -> this.friendJoinHandler = h); }
    public void setWebRtcSignalingHandler(@Nullable WebRtcSignalingHandler h) { executor.execute(() -> this.webRtcSignalingHandler = h); }
    public void addConnectionListener(ConnectionListener l) { connectionListeners.add(l); }
    public void removeConnectionListener(ConnectionListener l) { connectionListeners.remove(l); }
    public void clearHandlers() {
        executor.execute(() -> { this.friendJoinHandler = null; this.webRtcSignalingHandler = null; });
    }

    public void connect()    { executor.execute(this::connectWebSocket); }
    public void disconnect() { executor.execute(() -> teardown("explicit disconnect")); }

    // -------- TURN ----------------------------------------------------------

    public CompletableFuture<RTCIceServer> requestTurnAuth() {
        return CompletableFuture.completedFuture(null).thenComposeAsync(unused -> {
            CachedTurn cached = this.cachedTurn;
            if (cached != null && cached.isUsable()) return CompletableFuture.completedFuture(cached.turnAuth.toRtcIceServer());
            if (pendingTurnRefresh != null) return pendingTurnRefresh;
            CompletableFuture<RTCIceServer> refresh = refreshTurnAuth();
            pendingTurnRefresh = refresh;
            refresh.whenCompleteAsync((u, t) -> pendingTurnRefresh = null, executor);
            return refresh;
        }, executor);
    }

    private CompletableFuture<RTCIceServer> refreshTurnAuth() {
        return sendRequest(RPC_TURN_AUTH, List.of())
                .exceptionallyCompose(error -> CompletableFuture.failedFuture(
                        error.getCause() instanceof JsonRpcException jre
                                ? new SignalingException.TurnAuthFailedException(jre.serverMessage())
                                : error))
                .thenApplyAsync(result -> {
                    TurnAuthResult turnAuth;
                    try { turnAuth = TurnAuthResult.fromJson(result); }
                    catch (RuntimeException e) { throw new IllegalStateException("Malformed TurnAuth response: " + e.getMessage()); }
                    RTCIceServer ice = turnAuth.toRtcIceServer();
                    this.cachedTurn = new CachedTurn(turnAuth);
                    return ice;
                }, executor);
    }

    // -------- send a client-to-client message -------------------------------

    public CompletableFuture<Void> sendClientMessage(UUID toPlayerId, SignalingMessage message) {
        String encoded = message.toJson().toString();
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(u -> sendRequest(RPC_SEND_CLIENT, List.of(
                        JsonNull.INSTANCE,
                        new JsonPrimitive(toPlayerId.toString()),
                        new JsonPrimitive(encoded))), executor)
                .thenApply(v -> (Void) null)
                .exceptionallyCompose(err -> {
                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                    if (cause instanceof JsonRpcException rpcErr) {
                        SignalingException mapped = SignalingErrorMapper.fromJsonRpc(toPlayerId, rpcErr);
                        LOGGER.warn("Signaling rejected send: {}", mapped.getMessage());
                        fireListeners(l -> l.onSignalingError(toPlayerId, mapped));
                        return CompletableFuture.failedFuture(mapped);
                    }
                    return CompletableFuture.failedFuture(err);
                });
    }

    // -------- websocket lifecycle -------------------------------------------

    private void connectWebSocket() {
        if (this.websocketConnect != null) return;
        HttpClient client = HttpClient.newBuilder().build();
        this.httpClient = client;
        JsonRpcClient rpc = new JsonRpcClient(executor, this::onRpcMethod, this::onWebsocketDown);
        String requestId = UUID.randomUUID().toString();
        this.websocketConnect = getSignalingUri(client, requestId)
                .thenComposeAsync(wsUrl -> openWebSocket(client, rpc, wsUrl, requestId), executor);
        this.websocketConnect.whenCompleteAsync((unused, err) -> {
            if (err != null) {
                Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                if (!isSameHttpClientSession(client)) {
                    LOGGER.debug("Stale signaling connect attempt failed: {}", cause.toString());
                } else {
                    LOGGER.warn("Signaling websocket connect failed: {}", cause.toString());
                    cachedSignalingUri = null;
                    teardown("websocket connect failed: " + cause.getMessage());
                }
            }
        }, executor);
    }

    private void onWebsocketDown() {
        if (teardown("websocket closed")) {
            fireListeners(ConnectionListener::onSignalingDisconnected);
        }
    }

    private boolean teardown(String reason) {
        HttpClient client = this.httpClient;
        CompletableFuture<JsonRpcClient> connectFuture = this.websocketConnect;
        if (client == null || connectFuture == null) return false;
        LOGGER.debug("Signaling session disconnecting ({})", reason);
        if (pingTask != null) { pingTask.cancel(false); pingTask = null; }
        pendingTurnRefresh = null;
        connectFuture.whenComplete((rpc, t) -> {
            CompletableFuture<?> rpcClosed = rpc != null ? rpc.close() : CompletableFuture.completedFuture(null);
            rpcClosed.whenComplete((u, tt) -> CompletableFuture.runAsync(client::close));
        });
        connectFuture.completeExceptionally(new IllegalStateException("Signaling torn down: " + reason));
        this.httpClient = null;
        this.websocketConnect = null;
        return true;
    }

    private CompletableFuture<String> getSignalingUri(HttpClient client, String requestId) {
        CachedSignalingUri cached = cachedSignalingUri;
        if (cached != null && cached.isUsable()) return CompletableFuture.completedFuture(cached.wsUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENVIRONMENT.getConfigurationUri()))
                .header(HEADER_AUTH, user.accessToken())
                .header(HEADER_SESSION_ID, sessionId)
                .header(HEADER_REQUEST_ID, requestId)
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(resp -> {
                    if (resp.statusCode() != 200) {
                        throw new IllegalStateException("Unexpected config response status: " + resp.statusCode());
                    }
                    String baseUri;
                    try {
                        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                        baseUri = root.getAsJsonObject("result").get("signalingUri").getAsString();
                    } catch (RuntimeException e) {
                        throw new IllegalStateException("Malformed config response: " + e.getMessage());
                    }
                    String wsUrl = baseUri + WS_CONNECTION_ENDPOINT;
                    cachedSignalingUri = new CachedSignalingUri(wsUrl);
                    return wsUrl;
                }, executor);
    }

    private CompletableFuture<JsonRpcClient> openWebSocket(HttpClient client, JsonRpcClient rpc,
                                                           String wsUrl, String requestId) {
        if (!isSameHttpClientSession(client)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Signaling torn down before WebSocket open"));
        }
        return client.newWebSocketBuilder()
                .header(HEADER_AUTH, user.accessToken())
                .header(HEADER_SESSION_ID, sessionId)
                .header(HEADER_REQUEST_ID, requestId)
                .buildAsync(URI.create(wsUrl), rpc)
                .thenComposeAsync(ws -> {
                    if (isSameHttpClientSession(client)) {
                        schedulePing(rpc);
                        return CompletableFuture.completedFuture(rpc);
                    }
                    ws.abort();
                    return CompletableFuture.failedFuture(new IllegalStateException("Stale signaling WebSocket connection"));
                }, executor);
    }

    private void schedulePing(JsonRpcClient rpc) {
        pingTask = executor.scheduleAtFixedRate(() -> {
            try { rpc.sendNotification(RPC_PING); }
            catch (RuntimeException e) { LOGGER.warn("Signaling ping failed", e); }
        }, PING_INTERVAL.toMillis(), PING_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean isSameHttpClientSession(HttpClient client) { return this.httpClient == client; }

    private CompletableFuture<JsonElement> sendRequest(String method, List<JsonElement> params) {
        if (websocketConnect == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Signaling is not connected; call connect() first"));
        }
        return websocketConnect.thenCompose(r -> r.sendRequest(method, params));
    }

    // -------- inbound RPC dispatch ------------------------------------------

    private void onRpcMethod(JsonRpcClient rpc, @Nullable JsonElement id, String method, @Nullable JsonElement params) {
        switch (method) {
            case RPC_PONG -> { /* no-op */ }
            case RPC_RECEIVE -> handleReceiveMessage(rpc, id, params);
            default -> { if (id != null) rpc.sendError(id, JsonRPCErrors.METHOD_NOT_FOUND, method); }
        }
    }

    private void handleReceiveMessage(JsonRpcClient rpc, @Nullable JsonElement id, @Nullable JsonElement params) {
        JsonArray arr = (params != null && params.isJsonArray()) ? params.getAsJsonArray() : null;
        JsonElement first = (arr != null && !arr.isEmpty()) ? arr.get(0) : null;
        if (first == null || !first.isJsonObject()) {
            LOGGER.warn("Malformed ReceiveMessage params: {}", params);
            if (id != null) rpc.sendError(id, JsonRPCErrors.INVALID_PARAMS, "Expected [object] params");
            return;
        }
        if (id != null) rpc.sendResponse(id, new JsonObject());

        ClientWebRtcMessage msg;
        try { msg = ClientWebRtcMessage.fromJson(first.getAsJsonObject()); }
        catch (RuntimeException e) { LOGGER.warn("Malformed ReceiveMessage envelope: {}", e.getMessage()); return; }

        JsonElement inner;
        try { inner = JsonParser.parseString(msg.message); }
        catch (JsonSyntaxException e) { LOGGER.warn("Dropping non-JSON signaling payload: {}", e.getMessage()); return; }

        SignalingException serviceError = SignalingErrorMapper.fromServiceEnvelope(inner);
        if (serviceError != null) {
            LOGGER.debug("Signaling service reported error: {}", serviceError.getMessage());
            UUID errorPmid = serviceError.peerPmid() != null ? serviceError.peerPmid() : parseUuid(msg.from);
            fireListeners(l -> l.onSignalingError(errorPmid, serviceError));
            return;
        }

        SignalingMessage parsed;
        try { parsed = SignalingMessage.fromJson(inner.getAsJsonObject()); }
        catch (RuntimeException e) { LOGGER.warn("Malformed signaling payload: {}", e.getMessage()); return; }

        UUID fromPmid = parseUuid(msg.from);
        SignalingMessage.Payload p = parsed.decode();
        if (p == null) {
            LOGGER.debug("Ignoring malformed signaling message of type {}", parsed.type());
            return;
        }
        switch (p) {
            case SignalingMessage.FriendJoin fj -> dispatchFriendJoinMessage(fromPmid, fj);
            case SignalingMessage.WebRtc wr     -> dispatchWebRtcMessage(fromPmid, wr);
        }
    }

    private void dispatchFriendJoinMessage(UUID fromPmid, SignalingMessage.FriendJoin msg) {
        FriendJoinHandler h = this.friendJoinHandler;
        if (h == null) return;
        try { h.handle(fromPmid, msg); }
        catch (RuntimeException e) { LOGGER.error("Failed to dispatch FriendJoin", e); }
    }

    private void dispatchWebRtcMessage(UUID fromPmid, SignalingMessage.WebRtc msg) {
        WebRtcSignalingHandler h = this.webRtcSignalingHandler;
        if (h == null) return;
        try { h.handle(fromPmid, msg); }
        catch (RuntimeException e) { LOGGER.error("Failed to dispatch WebRtc", e); }
    }

    private void fireListeners(Consumer<ConnectionListener> action) {
        for (ConnectionListener l : connectionListeners) {
            try { action.accept(l); }
            catch (RuntimeException e) { LOGGER.error("ConnectionListener {} threw", l.getClass().getSimpleName(), e); }
        }
    }

    // -------- value records -------------------------------------------------

    private record CachedSignalingUri(String wsUrl, Instant expiresAt) {
        private static final Duration TTL = Duration.ofMinutes(5L);
        CachedSignalingUri(String wsUrl) { this(wsUrl, Instant.now().plus(TTL)); }
        boolean isUsable() { return Instant.now().isBefore(expiresAt); }
    }

    private record CachedTurn(TurnAuthResult turnAuth, Instant expiresAt) {
        private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60L);
        CachedTurn(TurnAuthResult turnAuth) { this(turnAuth, Instant.now().plusSeconds(turnAuth.expirationInSeconds())); }
        boolean isUsable() { return Instant.now().isBefore(expiresAt.minus(EXPIRY_MARGIN)); }
    }

    private record ClientWebRtcMessage(String from, String message, @Nullable UUID id) {
        static ClientWebRtcMessage fromJson(JsonObject obj) {
            String from = obj.get("From").getAsString();
            String msg  = obj.get("Message").getAsString();
            UUID id = (obj.has("Id") && !obj.get("Id").isJsonNull())
                    ? parseUuid(obj.get("Id").getAsString()) : null;
            return new ClientWebRtcMessage(from, msg, id);
        }
    }

    /** Parse UUID that may or may not have dashes (Mojang sends both formats). */
    private static UUID parseUuid(String s) {
        if (s.length() == 32 && !s.contains("-")) {
            // Insert dashes: 8-4-4-4-12
            s = s.substring(0,8) + "-" + s.substring(8,12) + "-" + s.substring(12,16)
                    + "-" + s.substring(16,20) + "-" + s.substring(20);
        }
        return UUID.fromString(s);
    }

    private record TurnAuthResult(long expirationInSeconds, List<TurnAuthServer> turnAuthServers) {
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
            TurnAuthServer first = turnAuthServers.getFirst();
            RTCIceServer ice = new RTCIceServer();
            ice.username = first.username;
            ice.password = first.password;
            for (TurnAuthServer s : turnAuthServers) ice.urls.addAll(s.urls);
            return ice;
        }
    }

    private record TurnAuthServer(String username, String password, List<String> urls) {
        static TurnAuthServer fromJson(JsonObject o) {
            String u = o.get("Username").getAsString();
            String p = o.get("Password").getAsString();
            List<String> us = new ArrayList<>();
            for (JsonElement el : o.getAsJsonArray("Urls")) us.add(el.getAsString());
            return new TurnAuthServer(u, p, us);
        }
    }

    /** Static convenience factory matching the old API. */
    public static UserCredentials staticCredentials(String accessToken, UUID profileId, String name) {
        return new UserCredentials() {
            @Override public String accessToken() { return accessToken; }
            @Override public UUID profileId()    { return profileId; }
            @Override public String userName()   { return name; }
        };
    }
}
