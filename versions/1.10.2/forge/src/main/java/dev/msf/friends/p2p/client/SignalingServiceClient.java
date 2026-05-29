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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Ported from 26.2 SignalingServiceClient. Replaces Java 11 HttpClient/WebSocket
 * with HttpURLConnection (for HTTP) and Netty WebSocket (via JsonRpcClient).
 */
public final class SignalingServiceClient {
    private static final Logger LOGGER = Logging.get();
    private static final String WS_CONNECTION_ENDPOINT = "/ws/v1.0/messaging/connect/java";
    private static final long PING_INTERVAL_MILLIS = 50000L;
    private static final String HEADER_AUTH = "x-mojangauth";
    private static final String HEADER_SESSION_ID = "Session-Id";
    private static final String HEADER_REQUEST_ID = "Request-Id";

    private static final String RPC_PING = MsfFriendsConstants.RPC_PING;
    private static final String RPC_PONG = MsfFriendsConstants.RPC_PONG;
    private static final String RPC_TURN_AUTH    = MsfFriendsConstants.RPC_TURN_AUTH;
    private static final String RPC_SEND_CLIENT  = MsfFriendsConstants.RPC_SEND_CLIENT;
    private static final String RPC_RECEIVE      = MsfFriendsConstants.RPC_RECEIVE;

    public enum Environment {
        STAGE("https://signaling-afd.stage-6fd5f759.franchise.minecraft-services.net"),
        PRODUCTION("https://signaling-afd.franchise.minecraft-services.net");

        private static final String CONFIGURATION_ENDPOINT = "/api/v1.0/configuration/java";
        public final String baseUrl;
        Environment(String baseUrl) { this.baseUrl = baseUrl; }
        public String getConfigurationUri() { return baseUrl + CONFIGURATION_ENDPOINT; }

        public static Environment byName(String name) {
            String lc = name.toLowerCase(Locale.ROOT);
            if ("stage".equals(lc) || "staging".equals(lc)) return STAGE;
            if ("prod".equals(lc) || "production".equals(lc)) return PRODUCTION;
            return null;
        }
    }

    public interface ConnectionListener {
        void onSignalingError(UUID peerPmid, SignalingException cause);
        void onSignalingDisconnected();
    }

    public interface FriendJoinHandler {
        void handle(UUID fromPmid, SignalingMessage.FriendJoin message);
    }

    public interface WebRtcSignalingHandler {
        void handle(UUID fromPmid, SignalingMessage.WebRtc message);
    }

    public interface UserCredentials {
        String accessToken();
        UUID profileId();
        String userName();
    }

    private static final Environment ENVIRONMENT;

    static {
        String envName = System.getProperty(MsfFriendsConstants.SIGNALING_ENV_PROP);
        if (envName == null) envName = System.getenv("signaling.environment");
        Environment e = null;
        if (envName != null) e = Environment.byName(envName);
        ENVIRONMENT = e != null ? e : Environment.PRODUCTION;
    }

    private final UserCredentials user;
    private final String sessionId = UUID.randomUUID().toString();
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "P2P-Signaling");
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((th, e) -> LOGGER.error("Uncaught in {}", th, e));
        return t;
    });

    private CompletableFuture<JsonRpcClient> websocketConnect;
    private ScheduledFuture<?> pingTask;
    private FriendJoinHandler friendJoinHandler;
    private WebRtcSignalingHandler webRtcSignalingHandler;
    private CachedTurn cachedTurn;
    private CachedSignalingUri cachedSignalingUri;
    private CompletableFuture<RTCIceServer> pendingTurnRefresh;
    private volatile boolean tornDown;

    public SignalingServiceClient(UserCredentials user) {
        this.user = user;
    }

    public Environment environment() { return ENVIRONMENT; }
    public String sessionId() { return sessionId; }

    public void setFriendJoinHandler(FriendJoinHandler h) { executor.execute(() -> this.friendJoinHandler = h); }
    public void setWebRtcSignalingHandler(WebRtcSignalingHandler h) { executor.execute(() -> this.webRtcSignalingHandler = h); }
    public void addConnectionListener(ConnectionListener l) { connectionListeners.add(l); }
    public void removeConnectionListener(ConnectionListener l) { connectionListeners.remove(l); }
    public void clearHandlers() {
        executor.execute(() -> { this.friendJoinHandler = null; this.webRtcSignalingHandler = null; });
    }

    public void connect()    { executor.execute(this::connectWebSocket); }
    public void disconnect() { executor.execute(() -> teardown("explicit disconnect")); }

    // -------- TURN ----------------------------------------------------------

    public CompletableFuture<RTCIceServer> requestTurnAuth() {
        return CompletableFuture.supplyAsync(() -> null, executor).thenCompose(unused -> {
            CachedTurn cached = this.cachedTurn;
            if (cached != null && cached.isUsable()) return CompletableFuture.completedFuture(cached.turnAuth.toRtcIceServer());
            if (pendingTurnRefresh != null) return pendingTurnRefresh;
            CompletableFuture<RTCIceServer> refresh = refreshTurnAuth();
            pendingTurnRefresh = refresh;
            refresh.whenComplete((u, t) -> executor.execute(() -> pendingTurnRefresh = null));
            return refresh;
        });
    }

    private CompletableFuture<RTCIceServer> refreshTurnAuth() {
        return sendRequest(RPC_TURN_AUTH, Collections.<JsonElement>emptyList())
                .thenApply(result -> {
                    TurnAuthResult turnAuth;
                    try { turnAuth = TurnAuthResult.fromJson(result); }
                    catch (RuntimeException e) { throw new IllegalStateException("Malformed TurnAuth response: " + e.getMessage()); }
                    RTCIceServer ice = turnAuth.toRtcIceServer();
                    executor.execute(() -> this.cachedTurn = new CachedTurn(turnAuth));
                    return ice;
                });
    }

    // -------- send a client-to-client message -------------------------------

    public CompletableFuture<Void> sendClientMessage(UUID toPlayerId, SignalingMessage message) {
        String encoded = message.toJson().toString();
        return sendRequest(RPC_SEND_CLIENT, makeList(
                (JsonElement) JsonNull.INSTANCE,
                new JsonPrimitive(toPlayerId.toString()),
                new JsonPrimitive(encoded)))
                .thenApply(v -> (Void) null)
                .exceptionally(err -> {
                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                    if (cause instanceof JsonRpcException) {
                        JsonRpcException rpcErr = (JsonRpcException) cause;
                        SignalingException mapped = SignalingErrorMapper.fromJsonRpc(toPlayerId, rpcErr);
                        LOGGER.warn("Signaling rejected send: {}", mapped.getMessage());
                        fireListeners(l -> l.onSignalingError(toPlayerId, mapped));
                        throw new CompletionException(mapped);
                    }
                    throw new CompletionException(cause);
                });
    }

    // -------- websocket lifecycle -------------------------------------------

    private void connectWebSocket() {
        if (tornDown || this.websocketConnect != null) return;
        JsonRpcClient rpc = new JsonRpcClient(executor, this::onRpcMethod, this::onWebsocketDown);
        String requestId = UUID.randomUUID().toString();
        this.websocketConnect = getSignalingUri(requestId)
                .thenCompose(wsUrl -> openWebSocket(rpc, wsUrl, requestId));
        this.websocketConnect.whenComplete((unused, err) -> {
            if (err != null) {
                Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                LOGGER.warn("Signaling websocket connect failed: {}", cause.toString());
                cachedSignalingUri = null;
                teardown("websocket connect failed: " + cause.getMessage());
            }
        });
    }

    private void onWebsocketDown() {
        if (teardown("websocket closed")) {
            fireListeners(ConnectionListener::onSignalingDisconnected);
        }
    }

    private boolean teardown(String reason) {
        if (tornDown) return false;
        tornDown = true;
        CompletableFuture<JsonRpcClient> connectFuture = this.websocketConnect;
        LOGGER.debug("Signaling session disconnecting ({})", reason);
        if (pingTask != null) { pingTask.cancel(false); pingTask = null; }
        pendingTurnRefresh = null;
        this.websocketConnect = null;
        if (connectFuture != null) {
            connectFuture.whenComplete((rpc, t) -> {
                if (rpc != null) rpc.close();
            });
            connectFuture.completeExceptionally(new IllegalStateException("Signaling torn down: " + reason));
        }
        return true;
    }

    private CompletableFuture<String> getSignalingUri(String requestId) {
        CachedSignalingUri cached = cachedSignalingUri;
        if (cached != null && cached.isUsable()) return CompletableFuture.completedFuture(cached.wsUrl);

        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENVIRONMENT.getConfigurationUri());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty(HEADER_AUTH, user.accessToken());
                conn.setRequestProperty(HEADER_SESSION_ID, sessionId);
                conn.setRequestProperty(HEADER_REQUEST_ID, requestId);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                int status = conn.getResponseCode();
                if (status != 200) throw new IOException("Unexpected config response status: " + status);
                JsonObject root;
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    root = new JsonParser().parse(reader).getAsJsonObject();
                }
                String baseUri = root.getAsJsonObject("result").get("signalingUri").getAsString();
                String wsUrl = baseUri + WS_CONNECTION_ENDPOINT;
                executor.execute(() -> cachedSignalingUri = new CachedSignalingUri(wsUrl));
                return wsUrl;
            } catch (IOException e) {
                throw new CompletionException(e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, executor);
    }

    private CompletableFuture<JsonRpcClient> openWebSocket(JsonRpcClient rpc, String wsUrl, String requestId) {
        if (tornDown) {
            CompletableFuture<JsonRpcClient> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException("Signaling torn down before WebSocket open"));
            return f;
        }

        Map<String, String> headers = new HashMap<String, String>();
        headers.put(HEADER_AUTH, user.accessToken());
        headers.put(HEADER_SESSION_ID, sessionId);
        headers.put(HEADER_REQUEST_ID, requestId);

        URI uri = URI.create(wsUrl);
        return rpc.connect(uri, headers).thenApply(ch -> {
            schedulePing(rpc);
            return rpc;
        });
    }

    private void schedulePing(JsonRpcClient rpc) {
        pingTask = executor.scheduleAtFixedRate(() -> {
            try { rpc.sendNotification(RPC_PING); }
            catch (RuntimeException e) { LOGGER.warn("Signaling ping failed", e); }
        }, PING_INTERVAL_MILLIS, PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<JsonElement> sendRequest(String method, List<JsonElement> params) {
        if (websocketConnect == null) {
            CompletableFuture<JsonElement> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalStateException(
                    "Signaling is not connected; call connect() first"));
            return f;
        }
        return websocketConnect.thenCompose(r -> r.sendRequest(method, params));
    }

    // -------- inbound RPC dispatch ------------------------------------------

    private void onRpcMethod(JsonRpcClient rpc, JsonElement id, String method, JsonElement params) {
        if (RPC_PONG.equals(method)) {
            // no-op
        } else if (RPC_RECEIVE.equals(method)) {
            handleReceiveMessage(rpc, id, params);
        } else {
            if (id != null) rpc.sendError(id, JsonRPCErrors.METHOD_NOT_FOUND);
        }
    }

    private void handleReceiveMessage(JsonRpcClient rpc, JsonElement id, JsonElement params) {
        JsonArray arr = (params != null && params.isJsonArray()) ? params.getAsJsonArray() : null;
        JsonElement first = (arr != null && arr.size() > 0) ? arr.get(0) : null;
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
        try { inner = new JsonParser().parse(msg.message); }
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
        if (p instanceof SignalingMessage.FriendJoin) {
            dispatchFriendJoinMessage(fromPmid, (SignalingMessage.FriendJoin) p);
        } else if (p instanceof SignalingMessage.WebRtc) {
            dispatchWebRtcMessage(fromPmid, (SignalingMessage.WebRtc) p);
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

    // -------- value types ---------------------------------------------------

    private static final class CachedSignalingUri {
        final String wsUrl;
        final Instant expiresAt;
        CachedSignalingUri(String wsUrl) { this.wsUrl = wsUrl; this.expiresAt = Instant.now().plus(Duration.ofMinutes(5)); }
        boolean isUsable() { return Instant.now().isBefore(expiresAt); }
    }

    private static final class CachedTurn {
        final TurnAuthResult turnAuth;
        final Instant expiresAt;
        CachedTurn(TurnAuthResult turnAuth) { this.turnAuth = turnAuth; this.expiresAt = Instant.now().plusSeconds(turnAuth.expirationInSeconds).minusSeconds(60); }
        boolean isUsable() { return Instant.now().isBefore(expiresAt); }
    }

    private static final class ClientWebRtcMessage {
        final String from;
        final String message;
        final UUID id;
        ClientWebRtcMessage(String from, String message, UUID id) { this.from = from; this.message = message; this.id = id; }
        static ClientWebRtcMessage fromJson(JsonObject obj) {
            String from = obj.get("From").getAsString();
            String msg  = obj.get("Message").getAsString();
            UUID id = (obj.has("Id") && !obj.get("Id").isJsonNull())
                    ? parseUuid(obj.get("Id").getAsString()) : null;
            return new ClientWebRtcMessage(from, msg, id);
        }
    }

    private static final class TurnAuthResult {
        final long expirationInSeconds;
        final List<TurnAuthServer> turnAuthServers;
        TurnAuthResult(long exp, List<TurnAuthServer> servers) { this.expirationInSeconds = exp; this.turnAuthServers = servers; }
        static TurnAuthResult fromJson(JsonElement root) {
            JsonObject o = root.getAsJsonObject();
            long exp = o.get("ExpirationInSeconds").getAsLong();
            List<TurnAuthServer> servers = new ArrayList<TurnAuthServer>();
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

    private static final class TurnAuthServer {
        final String username;
        final String password;
        final List<String> urls;
        TurnAuthServer(String u, String p, List<String> urls) { this.username = u; this.password = p; this.urls = urls; }
        static TurnAuthServer fromJson(JsonObject o) {
            String u = o.get("Username").getAsString();
            String p = o.get("Password").getAsString();
            List<String> us = new ArrayList<String>();
            for (JsonElement el : o.getAsJsonArray("Urls")) us.add(el.getAsString());
            return new TurnAuthServer(u, p, us);
        }
    }

    /** Parse UUID that may or may not have dashes (Mojang sends both formats). */
    static UUID parseUuid(String s) {
        if (s.length() == 32 && !s.contains("-")) {
            s = s.substring(0,8) + "-" + s.substring(8,12) + "-" + s.substring(12,16)
                    + "-" + s.substring(16,20) + "-" + s.substring(20);
        }
        return UUID.fromString(s);
    }

    /** Helper to build a list of JsonElement varargs. */
    private static List<JsonElement> makeList(JsonElement... elements) {
        List<JsonElement> list = new ArrayList<JsonElement>(elements.length);
        for (JsonElement e : elements) list.add(e);
        return list;
    }

    public static UserCredentials staticCredentials(final String accessToken, final UUID profileId, final String name) {
        return new UserCredentials() {
            @Override public String accessToken() { return accessToken; }
            @Override public UUID profileId()    { return profileId; }
            @Override public String userName()   { return name; }
        };
    }
}
