package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.msf.friends.MsfFriendsConstants;
import dev.msf.friends.p2p.SignalingErrorMapper;
import dev.msf.friends.p2p.SignalingException;
import dev.msf.friends.p2p.SignalingMessage;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SignalingServiceClient - Java 8 compatible version.
 * Uses HttpURLConnection for REST and a thread-based polling approach instead of
 * java.net.http.WebSocket (which requires Java 11+).
 */
public class SignalingServiceClient {
    private static final Logger LOGGER = Logging.get();
    private static final String SIGNALING_ENDPOINT = "wss://signal.mergilink.dev";
    private static final String REST_ENDPOINT = "https://signal.mergilink.dev";
    private static final long RECONNECT_DELAY_MS = 3000L;
    private static final long POLL_INTERVAL_MS = 5000L;

    private final UserCredentials credentials;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private volatile java.util.function.BiConsumer<UUID, SignalingMessage.FriendJoin> friendJoinHandler;
    private volatile java.util.function.BiConsumer<UUID, SignalingMessage.WebRtc> webRtcSignalingHandler;
    private final CopyOnWriteArrayList<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Signaling-Client");
            t.setDaemon(true);
            return t;
        }
    });

    public interface ConnectionListener {
        default void onSignalingDisconnected() {}
        default void onSignalingError(UUID peerPmid, SignalingException cause) {}
    }

    public static class UserCredentials {
        public final UUID profileId;
        public final String accessToken;
        public final String friendsToken;

        public UserCredentials(UUID profileId, String accessToken, String friendsToken) {
            this.profileId = profileId;
            this.accessToken = accessToken;
            this.friendsToken = friendsToken;
        }
    }

    public SignalingServiceClient(UserCredentials credentials) {
        this.credentials = credentials;
    }

    public void setFriendJoinHandler(java.util.function.BiConsumer<UUID, SignalingMessage.FriendJoin> handler) {
        this.friendJoinHandler = handler;
    }

    public void setWebRtcSignalingHandler(java.util.function.BiConsumer<UUID, SignalingMessage.WebRtc> handler) {
        this.webRtcSignalingHandler = handler;
    }

    public void addConnectionListener(ConnectionListener listener) { connectionListeners.add(listener); }
    public void removeConnectionListener(ConnectionListener listener) { connectionListeners.remove(listener); }

    public void connect() {
        if (connected.get() || shutdown.get()) return;
        // REST-based register + mark connected
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    String url = REST_ENDPOINT + "/register";
                    JsonObject body = new JsonObject();
                    body.addProperty("profileId", credentials.profileId.toString());
                    body.addProperty("token", credentials.friendsToken);
                    int code = httpPost(url, body.toString());
                    if (code == 200 || code == 204) {
                        connected.set(true);
                        LOGGER.info("[signaling] Connected successfully");
                    } else {
                        LOGGER.warn("[signaling] Registration failed with code {}", code);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[signaling] Failed to connect", e);
                }
            }
        });
    }

    public void disconnect() {
        connected.set(false);
        try {
            String url = REST_ENDPOINT + "/unregister";
            JsonObject body = new JsonObject();
            body.addProperty("profileId", credentials.profileId.toString());
            httpPost(url, body.toString());
        } catch (Exception e) {
            // Ignore
        }
        connectionListeners.forEach(new java.util.function.Consumer<ConnectionListener>() {
            @Override public void accept(ConnectionListener l) { l.onSignalingDisconnected(); }
        });
    }

    public void clearHandlers() {
        friendJoinHandler = null;
        webRtcSignalingHandler = null;
    }

    public CompletableFuture<Void> sendClientMessage(UUID peerPmid, SignalingMessage message) {
        if (!connected.get()) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new SignalingException.SignalingRejectedException(peerPmid, "Not connected"));
            return f;
        }
        return CompletableFuture.runAsync(new Runnable() {
            @Override public void run() {
                try {
                    String url = REST_ENDPOINT + "/send";
                    JsonObject body = message.toJson();
                    body.addProperty("from", credentials.profileId.toString());
                    body.addProperty("to", peerPmid.toString());
                    body.addProperty("accessToken", credentials.friendsToken);
                    int code = httpPost(url, body.toString());
                    if (code != 200 && code != 204) {
                        throw new SignalingException.SignalingRejectedException(peerPmid, "Send failed: " + code);
                    }
                } catch (SignalingException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SignalingException.MessageUndeliveredException(e.getMessage());
                }
            }
        }, executor);
    }

    public CompletableFuture<dev.onvoid.webrtc.RTCIceServer> requestTurnAuth() {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<dev.onvoid.webrtc.RTCIceServer>() {
            @Override public dev.onvoid.webrtc.RTCIceServer get() {
                try {
                    String url = REST_ENDPOINT + "/turn";
                    JsonObject body = new JsonObject();
                    body.addProperty("token", credentials.friendsToken);
                    String response = httpPostWithResponse(url, body.toString());
                    JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
                    dev.onvoid.webrtc.RTCIceServer server = new dev.onvoid.webrtc.RTCIceServer();
                    server.urls.add(obj.get("url").getAsString());
                    if (obj.has("username")) server.username = obj.get("username").getAsString();
                    if (obj.has("credential")) server.password = obj.get("credential").getAsString();
                    return server;
                } catch (Exception e) {
                    throw new CompletionException(new SignalingException.TurnAuthFailedException(e.getMessage()));
                }
            }
        }, executor);
    }

    // --- HTTP helpers ---

    private int httpPost(String endpoint, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return conn.getResponseCode();
    }

    private String httpPostWithResponse(String endpoint, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
