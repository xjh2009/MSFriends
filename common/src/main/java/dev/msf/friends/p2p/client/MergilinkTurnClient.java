package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.msf.friends.util.Logging;
import dev.onvoid.webrtc.RTCIceServer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches TURN server credentials from the Mergilink public node list.
 *
 * <p>Endpoint: {@code https://gh-proxy.org/https://github.com/MergiLink/nodelist/raw/refs/heads/main/turn.json}
 *
 * <p>Expected response shape:
 * <pre>{@code
 * {
 *   "TurnAuthServers": [
 *     {"username": "...", "password": "...", "Urls": ["turn:...", "stun:..."]}
 *   ],
 *   "update_time": "2026-05-16-1"
 * }
 * }</pre>
 *
 * <p>Results are cached for {@value #CACHE_MINUTES} minutes.
 */
public final class MergilinkTurnClient {

    private static final Logger LOGGER = Logging.get();
    private static final String TURN_URL =
            "https://gh-proxy.org/https://github.com/MergiLink/nodelist/raw/refs/heads/main/turn.json";
    private static final int CACHE_MINUTES = 10;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile List<RTCIceServer> cached = null;
    private volatile Instant cacheExpiry = Instant.MIN;
    private volatile CompletableFuture<List<RTCIceServer>> pending = null;

    /**
     * Returns a future that resolves to the Mergilink TURN server list.
     * Results are cached; on fetch failure returns an empty list so the caller
     * can still attempt a connection via other ICE candidates.
     */
    public synchronized CompletableFuture<List<RTCIceServer>> fetchTurnServers() {
        if (cached != null && Instant.now().isBefore(cacheExpiry)) {
            return CompletableFuture.completedFuture(cached);
        }
        if (pending != null) return pending;

        CompletableFuture<List<RTCIceServer>> future = doFetch();
        pending = future;
        future.whenComplete((result, err) -> {
            synchronized (MergilinkTurnClient.this) {
                pending = null;
                if (result != null && !result.isEmpty()) {
                    cached = result;
                    cacheExpiry = Instant.now().plus(Duration.ofMinutes(CACHE_MINUTES));
                }
            }
        });
        return future;
    }

    private CompletableFuture<List<RTCIceServer>> doFetch() {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                    .uri(URI.create(TURN_URL))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[mergilink-turn] Invalid TURN URL: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }

        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        LOGGER.warn("[mergilink-turn] TURN fetch returned HTTP {}", resp.statusCode());
                        return List.<RTCIceServer>of();
                    }
                    List<RTCIceServer> servers = parseJson(resp.body());
                    LOGGER.info("[mergilink-turn] Fetched {} TURN server(s)", servers.size());
                    return servers;
                })
                .exceptionally(err -> {
                    LOGGER.warn("[mergilink-turn] Failed to fetch TURN servers: {}", err.getMessage());
                    return List.of();
                });
    }

    private static List<RTCIceServer> parseJson(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            JsonArray servers;
            if (root.isJsonArray()) {
                // New format: top-level array
                servers = root.getAsJsonArray();
            } else {
                // Old format: {"TurnAuthServers": [...]}
                servers = root.getAsJsonObject().getAsJsonArray("TurnAuthServers");
            }
            List<RTCIceServer> result = new ArrayList<>();
            for (JsonElement el : servers) {
                JsonObject obj = el.getAsJsonObject();
                RTCIceServer ice = new RTCIceServer();
                String u = getStrCI(obj, "username", "");
                String p = getStrCI(obj, "password", "");
                ice.username = "null".equals(u) ? "" : u;
                ice.password = "null".equals(p) ? "" : p;
                JsonArray urls = getArrCI(obj, "urls");
                if (urls == null) continue;
                for (JsonElement url : urls) {
                    ice.urls.add(url.getAsString());
                }
                if (!ice.urls.isEmpty()) {
                    result.add(ice);
                }
            }
            return result;
        } catch (Exception e) {
            LOGGER.warn("[mergilink-turn] Failed to parse TURN JSON: {}", e.getMessage());
            return List.of();
        }
    }

    /** 大小写不敏感地从 JsonObject 取字符串字段，找不到返回 defaultVal */
    private static String getStrCI(JsonObject obj, String key, String defaultVal) {
        for (var entry : obj.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                JsonElement v = entry.getValue();
                return v.isJsonNull() ? defaultVal : v.getAsString();
            }
        }
        return defaultVal;
    }

    /** 大小写不敏感地从 JsonObject 取数组字段，找不到返回 null */
    private static JsonArray getArrCI(JsonObject obj, String key) {
        for (var entry : obj.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue().isJsonArray()) {
                return entry.getValue().getAsJsonArray();
            }
        }
        return null;
    }
}
