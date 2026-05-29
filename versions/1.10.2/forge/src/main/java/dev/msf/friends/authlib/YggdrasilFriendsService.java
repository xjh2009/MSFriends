package dev.msf.friends.authlib;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.msf.friends.authlib.request.JoinInfoUpdate;
import dev.msf.friends.authlib.response.FriendData;
import dev.msf.friends.authlib.response.FriendDto;
import dev.msf.friends.authlib.response.FriendsListResponse;
import dev.msf.friends.authlib.response.PresenceResponse;
import dev.msf.friends.authlib.response.PresenceStatusDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ported from authlib 7.0.72 YggdrasilFriendsService (Java 8 compatible).
 * Uses plain HttpURLConnection for all HTTP calls and Gson for serialization.
 */
public class YggdrasilFriendsService implements FriendsService {

    private static final Logger LOGGER = LogManager.getLogger("MSF/Friends");

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final long REQUEST_COOLDOWN_SECONDS = 10L;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final Gson gson = new GsonBuilder().create();

    private final String accessToken;
    private final java.net.Proxy proxy;
    private final String routeFriends;
    private final String routePrivileges;
    private final String routePresence;

    private FriendData friendsCache = FriendData.empty();
    private PresenceResponse presenceCache = PresenceResponse.empty();
    private volatile long requestCooldownMillis = 0L;
    private final AtomicBoolean requestPending = new AtomicBoolean(false);

    public YggdrasilFriendsService(String accessToken, java.net.Proxy proxy,
            String routeFriends, String routePrivileges, String routePresence) {
        this.accessToken = accessToken;
        this.proxy = proxy;
        this.routeFriends = routeFriends;
        this.routePrivileges = routePrivileges;
        this.routePresence = routePresence;
    }

    /**
     * Convenience constructor from MinecraftBridge, using correct Mojang API routes.
     * Note: /friends and /presence are directly under the services host, not under /player.
     */
    public YggdrasilFriendsService(dev.msf.friends.bridge.MinecraftBridge bridge) {
        this(bridge.accessToken(),
             java.net.Proxy.NO_PROXY,
             "https://api.minecraftservices.com/friends",
             "https://api.minecraftservices.com/player/attributes",
             "https://api.minecraftservices.com/presence");
    }

    @Override
    public ResultCode getFriendData(java.util.function.Consumer<FriendData> consumer) {
        if (requestPending.getAndSet(true)) {
            while (requestPending.get()) Thread.yield();
            consumer.accept(friendsCache);
            return ResultCode.SUCCESS;
        }

        if (!canMakeRequest()) {
            requestPending.set(false);
            consumer.accept(friendsCache);
            return ResultCode.SUCCESS;
        }
        requestCooldownMillis = System.currentTimeMillis() + REQUEST_COOLDOWN_SECONDS * 1000L;

        ResultCode code = requestFriendData(consumer);
        requestPending.set(false);
        return code;
    }

    private ResultCode requestFriendData(java.util.function.Consumer<FriendData> consumer) {
        try {
            String json = httpGet(routeFriends);
            FriendsListResponse resp = parseJson(json, FriendsListResponse.class);
            if (resp == null) resp = new FriendsListResponse(null, null, null);
            friendsCache = new FriendData(resp.friends(), resp.incomingRequests(), resp.outgoingRequests());
            consumer.accept(friendsCache);
            return ResultCode.SUCCESS;
        } catch (HttpException e) {
            return handleHttpError(e);
        } catch (IOException e) {
            LOGGER.warn("Friends list request failed", e);
            return ResultCode.CONNECTION_ISSUE;
        } catch (Exception e) {
            LOGGER.warn("Friends list request failed unexpectedly", e);
            return ResultCode.ERROR;
        }
    }

    private boolean canMakeRequest() {
        return System.currentTimeMillis() > requestCooldownMillis;
    }

    @Override
    public ResultCode removeFriend(UUID profileId) {
        return putFriendAction(profileId, null, "REMOVE");
    }

    @Override
    public ResultCode acceptIncomingFriendRequest(UUID profileId) {
        return putFriendAction(profileId, null, "ADD");
    }

    @Override
    public ResultCode declineIncomingFriendRequest(UUID profileId) {
        return putFriendAction(profileId, null, "REMOVE");
    }

    @Override
    public ResultCode sendFriendRequest(String name) {
        return putFriendAction(null, name, "ADD");
    }

    @Override
    public ResultCode sendFriendRequest(UUID profileId) {
        return putFriendAction(profileId, null, "ADD");
    }

    @Override
    public ResultCode revokeOutgoingFriendRequest(UUID profileId) {
        return putFriendAction(profileId, null, "REMOVE");
    }

    @Override
    public ResultCode updateFriendSettings(boolean friendsEnabled, boolean acceptInvites) {
        String friends = friendsEnabled ? "ENABLED" : "DISABLED";
        String invites = acceptInvites ? "ENABLED" : "DISABLED";
        String body = "{\"friendsPreferences\":{\"friends\":\"" + friends + "\",\"acceptInvites\":\"" + invites + "\"}}";
        try {
            httpPost(routePrivileges, body);
            return ResultCode.SUCCESS;
        } catch (HttpException e) {
            return handleHttpError(e);
        } catch (IOException e) {
            LOGGER.warn("updateFriendSettings request failed", e);
            return ResultCode.CONNECTION_ISSUE;
        } catch (Exception e) {
            LOGGER.warn("updateFriendSettings request failed unexpectedly", e);
            return ResultCode.ERROR;
        }
    }

    @Override
    public PresenceResponse presence(String status, JoinInfoUpdate joinInfo) {
        try {
            String body = buildPresenceJson(status, joinInfo);
            String json = httpPost(routePresence, body);
            PresenceResponse resp = parseJson(json, PresenceResponse.class);
            if (resp != null) presenceCache = resp;
        } catch (Exception e) {
            LOGGER.warn("Presence request failed", e);
        }
        return presenceCache;
    }

    // --------- helpers ---------

    private ResultCode putFriendAction(UUID profileId, String name, String updateType) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (name != null) {
            sb.append("\"name\":\"").append(escapeJson(name)).append("\"");
            first = false;
        }
        if (profileId != null) {
            if (!first) sb.append(",");
            sb.append("\"profileId\":\"").append(profileId.toString().replace("-", "")).append("\"");
            first = false;
        }
        if (!first) sb.append(",");
        sb.append("\"updateType\":\"").append(updateType).append("\"");
        sb.append("}");
        try {
            httpPut(routeFriends, sb.toString());
            return ResultCode.SUCCESS;
        } catch (HttpException e) {
            return handleHttpError(e);
        } catch (IOException e) {
            LOGGER.warn("Friend action request failed", e);
            return ResultCode.CONNECTION_ISSUE;
        } catch (Exception e) {
            LOGGER.warn("Friend action request failed unexpectedly", e);
            return ResultCode.ERROR;
        }
    }

    private ResultCode handleHttpError(HttpException e) {
        int code = e.getStatus();
        if (code == HTTP_TOO_MANY_REQUESTS) {
            return ResultCode.TOO_MANY_REQUESTS;
        }
        if (code >= 500) {
            return ResultCode.SERVICE_NOT_AVAILABLE;
        }
        if (code == 403) {
            return ResultCode.FORBIDDEN;
        }
        if (code == 400) {
            return ResultCode.UNKNOWN_PROFILE;
        }
        return ResultCode.GENERIC_ERROR;
    }

    private String buildPresenceJson(String status, JoinInfoUpdate joinInfo) {
        Map<String, Object> req = new HashMap<>();
        req.put("status", status);
        if (joinInfo != null) {
            Map<String, Object> ji = new HashMap<>();
            if (joinInfo.value() != null) ji.put("value", joinInfo.value());
            if (joinInfo.invites() != null && !joinInfo.invites().isEmpty()) {
                List<String> ids = new ArrayList<>();
                for (UUID id : joinInfo.invites()) ids.add(id.toString().replace("-", ""));
                ji.put("invites", ids);
            }
            req.put("joinInfo", ji);
        }
        return gson.toJson(req);
    }

    // --------- raw HTTP ---------

    private String httpGet(String urlStr) throws IOException, HttpException {
        LOGGER.info("[http] GET {}", urlStr);
        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        int status = conn.getResponseCode();
        if (status >= 400) {
            String errBody = readFully(conn.getErrorStream());
            LOGGER.info("[http] GET {} -> {} | error: {}", urlStr, status, errBody);
            conn.disconnect();
            throw new HttpException(status);
        }
        String body = readFully(conn.getInputStream());
        LOGGER.info("[http] GET {} -> {}", urlStr, status);
        conn.disconnect();
        return body;
    }

    private String httpPost(String urlStr, String jsonBody) throws IOException, HttpException {
        return httpSend("POST", urlStr, jsonBody);
    }

    private void httpPut(String urlStr, String jsonBody) throws IOException, HttpException {
        httpSend("PUT", urlStr, jsonBody);
    }

    private String httpSend(String method, String urlStr, String jsonBody) throws IOException, HttpException {
        LOGGER.info("[http] {} {}", method, urlStr);
        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        if (jsonBody != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = conn.getResponseCode();
        if (status >= 400) {
            String errBody = readFully(conn.getErrorStream());
            LOGGER.info("[http] {} {} -> {} | error: {}", method, urlStr, status, errBody);
            conn.disconnect();
            throw new HttpException(status);
        }
        String body = readFully(conn.getInputStream());
        LOGGER.info("[http] {} {} -> {}", method, urlStr, status);
        conn.disconnect();
        return body;
    }

    private HttpURLConnection openConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        return (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
    }

    private static String readFully(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private <T> T parseJson(String json, Class<T> cls) {
        return gson.fromJson(json, cls);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
