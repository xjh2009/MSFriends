package dev.msf.friends.p2p.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onvoid.webrtc.RTCIceServer;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * MergilinkTurnClient - Java 8 compatible version.
 */
public class MergilinkTurnClient {
    private static final Logger LOGGER = Logging.get();
    private static final String TURN_ENDPOINT = "https://signal.mergilink.dev/turn-servers";
    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Mergilink-Turn");
            t.setDaemon(true);
            return t;
        }
    });

    public CompletableFuture<List<RTCIceServer>> fetchTurnServers() {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<List<RTCIceServer>>() {
            @Override public List<RTCIceServer> get() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(TURN_ENDPOINT).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    try (InputStream is = conn.getInputStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        JsonArray arr = new JsonParser().parse(sb.toString()).getAsJsonArray();
                        List<RTCIceServer> servers = new ArrayList<>();
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject obj = arr.get(i).getAsJsonObject();
                            RTCIceServer server = new RTCIceServer();
                            server.urls.add(obj.get("url").getAsString());
                            if (obj.has("username")) server.username = obj.get("username").getAsString();
                            if (obj.has("credential")) server.password = obj.get("credential").getAsString();
                            servers.add(server);
                        }
                        return servers;
                    }
                } catch (Exception e) {
                    LOGGER.warn("[turn] Failed to fetch TURN servers", e);
                    return Collections.emptyList();
                }
            }
        }, executor);
    }
}
