package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.msf.friends.util.Logging;
import dev.onvoid.webrtc.RTCIceServer;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches TURN server credentials from the Mergilink public node list.
 * Ported from 26.2 — uses HttpURLConnection instead of Java 11 HttpClient.
 */
public final class MergilinkTurnClient {
    private static final Logger LOGGER = Logging.get();
    private static final String TURN_URL =
            "https://gh-proxy.org/https://github.com/MergiLink/nodelist/raw/refs/heads/main/turn.json";
    private static final int CACHE_MINUTES = 10;

    private final ExecutorService fetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mergilink-Turn-Fetch");
        t.setDaemon(true);
        return t;
    });

    private volatile List<RTCIceServer> cached = null;
    private volatile Instant cacheExpiry = Instant.MIN;
    private volatile CompletableFuture<List<RTCIceServer>> pending = null;

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
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(TURN_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "msf-friends-mod/1.0");
                int status = conn.getResponseCode();
                if (status != 200) {
                    LOGGER.warn("[mergilink-turn] TURN fetch returned HTTP {}", status);
                    return Collections.<RTCIceServer>emptyList();
                }
                String body;
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[4096];
                    int n;
                    while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
                    body = sb.toString();
                }
                List<RTCIceServer> servers = parseJson(body);
                LOGGER.info("[mergilink-turn] Fetched {} TURN server(s)", servers.size());
                return servers;
            } catch (Exception e) {
                LOGGER.warn("[mergilink-turn] Failed to fetch TURN servers: {}", e.getMessage());
                return Collections.<RTCIceServer>emptyList();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, fetchExecutor);
    }

    private static List<RTCIceServer> parseJson(String body) {
        try {
            JsonElement root = new JsonParser().parse(body);
            JsonArray servers;
            if (root.isJsonArray()) {
                servers = root.getAsJsonArray();
            } else {
                servers = root.getAsJsonObject().getAsJsonArray("TurnAuthServers");
            }
            List<RTCIceServer> result = new ArrayList<RTCIceServer>();
            for (JsonElement el : servers) {
                JsonObject obj = el.getAsJsonObject();
                RTCIceServer ice = new RTCIceServer();
                String u = getStrCI(obj, "username", "");
                String p = getStrCI(obj, "password", "");
                ice.username = "null".equals(u) ? "" : u;
                ice.password = "null".equals(p) ? "" : p;
                JsonArray urls = getArrCI(obj, "urls");
                if (urls == null) continue;
                for (JsonElement url : urls) ice.urls.add(url.getAsString());
                if (!ice.urls.isEmpty()) result.add(ice);
            }
            return result;
        } catch (Exception e) {
            LOGGER.warn("[mergilink-turn] Failed to parse TURN JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String getStrCI(JsonObject obj, String key, String defaultVal) {
        for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                JsonElement v = entry.getValue();
                return v.isJsonNull() ? defaultVal : v.getAsString();
            }
        }
        return defaultVal;
    }

    private static JsonArray getArrCI(JsonObject obj, String key) {
        for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue().isJsonArray()) {
                return entry.getValue().getAsJsonArray();
            }
        }
        return null;
    }
}
