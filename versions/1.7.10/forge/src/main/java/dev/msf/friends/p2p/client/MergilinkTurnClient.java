package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.msf.friends.util.Logging;
import dev.onvoid.webrtc.RTCIceServer;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches TURN server credentials from the Mergilink public node list.
 * Endpoint: https://gh-proxy.org/https://github.com/MergiLink/nodelist/raw/refs/heads/main/turn.json
 * Expected response shape: { "TurnAuthServers": [...] } or top-level array
 * Results are cached for CACHE_MINUTES minutes.
 * Uses HttpURLConnection instead of java.net.http (Java 8 compatible).
 */
public final class MergilinkTurnClient {

    private static final Logger LOGGER = Logging.get(MergilinkTurnClient.class);
    private static final String TURN_URL =
            "https://gh-proxy.org/https://github.com/MergiLink/nodelist/raw/refs/heads/main/turn.json";
    private static final int CACHE_MINUTES = 10;

    private final ExecutorService fetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MergilinkTurn-Fetch");
        t.setDaemon(true);
        return t;
    });

    private volatile List<RTCIceServer> cached = null;
    private volatile long cacheExpiryEpochMs = 0L;
    private volatile CompletableFuture<List<RTCIceServer>> pending = null;

    public synchronized CompletableFuture<List<RTCIceServer>> fetchTurnServers() {
        if (cached != null && System.currentTimeMillis() < cacheExpiryEpochMs) {
            return CompletableFuture.completedFuture(cached);
        }
        if (pending != null) return pending;

        CompletableFuture<List<RTCIceServer>> future = doFetch();
        pending = future;
        future.whenComplete(new java.util.function.BiConsumer<List<RTCIceServer>, Throwable>() {
            @Override public void accept(List<RTCIceServer> result, Throwable err) {
                synchronized (MergilinkTurnClient.this) {
                    pending = null;
                    if (result != null && !result.isEmpty()) {
                        cached = result;
                        cacheExpiryEpochMs = System.currentTimeMillis() + (long) CACHE_MINUTES * 60L * 1000L;
                    }
                }
            }
        });
        return future;
    }

    private CompletableFuture<List<RTCIceServer>> doFetch() {
        CompletableFuture<List<RTCIceServer>> future = new CompletableFuture<List<RTCIceServer>>();
        fetchExecutor.submit(new Runnable() {
            @Override public void run() {
                try {
                    future.complete(fetchSync());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    private List<RTCIceServer> fetchSync() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(TURN_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "MSF-Friends/1.0");

            int status = conn.getResponseCode();
            if (status != 200) {
                LOGGER.warn("[mergilink-turn] TURN fetch returned HTTP {}", status);
                return Collections.emptyList();
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            List<RTCIceServer> servers = parseJson(sb.toString());
            LOGGER.info("[mergilink-turn] Fetched {} TURN server(s)", servers.size());
            return servers;
        } catch (Exception e) {
            LOGGER.warn("[mergilink-turn] Failed to fetch TURN servers: {}", e.getMessage());
            return Collections.emptyList();
        } finally {
            if (conn != null) conn.disconnect();
        }
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
