package com.mojang.authlib.yggdrasil;

import com.mojang.authlib.Environment;
import com.mojang.authlib.exceptions.MinecraftClientHttpException;
import com.mojang.authlib.minecraft.client.ObjectMapper;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.FriendData;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import com.mojang.authlib.yggdrasil.response.FriendsListResponse;
import com.mojang.authlib.yggdrasil.response.PresenceResponse;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Bundled implementation of authlib 7.0.72 YggdrasilFriendsService.
 * Used on authlib 7.0.63 runtimes (MC 26.1.2 vanilla) where this class
 * does not exist in the shipped authlib jar.
 *
 * <p>Uses plain {@link HttpURLConnection} for all HTTP calls (including PUT)
 * so it never references the new {@code MinecraftClient.put/delete/getWithEtag}
 * methods absent in 7.0.63.
 */
public class YggdrasilFriendsService implements FriendsService {

    private static final Logger LOGGER = Logging.get();

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final long REQUEST_COOLDOWN_SECONDS = 10L;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final String accessToken;
    private final Proxy proxy;
    private final String routeFriends;
    private final String routePrivileges;
    private final String routePresence;

    /** Re-used Gson wrapper from authlib (handles UUID type adapters). */
    private final ObjectMapper mapper = ObjectMapper.create();

    private FriendData friendsCache = FriendData.empty();
    private PresenceResponse presenceCache = PresenceResponse.empty();
    private Instant requestCooldown = Instant.MIN;
    private final AtomicBoolean requestPending = new AtomicBoolean(false);

    public YggdrasilFriendsService(String accessToken, Proxy proxy, Environment environment) {
        this.accessToken = accessToken;
        this.proxy = proxy;
        this.routeFriends    = environment.servicesHost() + "/friends";
        this.routePrivileges = environment.servicesHost() + "/player/attributes";
        this.routePresence   = environment.servicesHost() + "/presence";
    }

    // -------------------------------------------------------------------------
    // FriendsService interface
    // -------------------------------------------------------------------------

    @Override
    public ResultCode getFriendData(Consumer<FriendData> consumer) {
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
        requestCooldown = Instant.now().plusSeconds(REQUEST_COOLDOWN_SECONDS);

        ResultCode code = requestFriendData(consumer);
        requestPending.set(false);
        return code;
    }

    private ResultCode requestFriendData(Consumer<FriendData> consumer) {
        try {
            String json = httpGet(routeFriends);
            FriendsListResponse resp = mapper.readValue(json, FriendsListResponse.class);
            List<FriendDto> friends   = resp.friends()          != null ? resp.friends()          : List.of();
            List<FriendDto> incoming  = resp.incomingRequests() != null ? resp.incomingRequests() : List.of();
            List<FriendDto> outgoing  = resp.outgoingRequests() != null ? resp.outgoingRequests() : List.of();
            friendsCache = new FriendData(friends, incoming, outgoing);
            consumer.accept(friendsCache);
            return ResultCode.SUCCESS;
        } catch (MinecraftClientHttpException e) {
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
        return Instant.now().isAfter(requestCooldown);
    }

    @Override
    public ResultCode removeFriend(UUID profileId) {
        return putFriendAction(FriendActionRequest.byId(profileId, UpdateType.REMOVE));
    }

    @Override
    public ResultCode acceptIncomingFriendRequest(UUID profileId) {
        return putFriendAction(FriendActionRequest.byId(profileId, UpdateType.ADD));
    }

    @Override
    public ResultCode declineIncomingFriendRequest(UUID profileId) {
        return putFriendAction(FriendActionRequest.byId(profileId, UpdateType.REMOVE));
    }

    @Override
    public ResultCode sendFriendRequest(String name) {
        return putFriendAction(FriendActionRequest.byName(name, UpdateType.ADD));
    }

    @Override
    public ResultCode sendFriendRequest(UUID profileId) {
        return putFriendAction(FriendActionRequest.byId(profileId, UpdateType.ADD));
    }

    @Override
    public ResultCode revokeOutgoingFriendRequest(UUID profileId) {
        return putFriendAction(FriendActionRequest.byId(profileId, UpdateType.REMOVE));
    }

    @Override
    public ResultCode updateFriendSettings(boolean friendsEnabled, boolean acceptInvites) {
        ToggleValue friends = friendsEnabled ? ToggleValue.ENABLED  : ToggleValue.DISABLED;
        ToggleValue invites = acceptInvites  ? ToggleValue.ENABLED  : ToggleValue.DISABLED;
        // Build a minimal JSON payload matching the server's UserAttributesRequest schema
        String body = "{\"friendsPreferences\":{\"friends\":\"" + friends.name()
                + "\",\"acceptInvites\":\"" + invites.name() + "\"}}";
        try {
            httpPost(routePrivileges, body);
            return ResultCode.SUCCESS;
        } catch (MinecraftClientHttpException e) {
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
            PresenceResponse resp = mapper.readValue(json, PresenceResponse.class);
            List<PresenceStatusDto> list = resp.presence() != null ? resp.presence() : List.of();
            presenceCache = new PresenceResponse(list);
        } catch (Exception e) {
            LOGGER.warn("Presence request failed", e);
        }
        return presenceCache;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private ResultCode putFriendAction(FriendActionRequest request) {
        try {
            httpPut(routeFriends, request.toJson(), Void.class);
            return ResultCode.SUCCESS;
        } catch (MinecraftClientHttpException e) {
            return handleHttpError(e);
        } catch (IOException e) {
            LOGGER.warn("Friend action request failed", e);
            return ResultCode.CONNECTION_ISSUE;
        } catch (Exception e) {
            LOGGER.warn("Friend action request failed unexpectedly", e);
            return ResultCode.ERROR;
        }
    }

    private ResultCode handleHttpError(MinecraftClientHttpException e) {
        int code = e.getStatus();
        if (code == HTTP_TOO_MANY_REQUESTS) {
            LOGGER.warn("Friends service rate-limited (429) — back off before retrying");
            return ResultCode.TOO_MANY_REQUESTS;
        }
        if (code >= 500) {
            LOGGER.warn("Friends service unavailable ({}) — retry later", code);
            return ResultCode.SERVICE_NOT_AVAILABLE;
        }
        if (code == 403) {
            LOGGER.warn("Friends service forbidden (403) — user may lack an active profile");
            return ResultCode.FORBIDDEN;
        }
        if (code == 400) {
            LOGGER.warn("Friends service forbidden (400) — Name or profile does not exist");
            return ResultCode.UNKNOWN_PROFILE;
        }
        LOGGER.warn("Friends service returned HTTP {} — {}", code, e);
        return ResultCode.GENERIC_ERROR;
    }

    // -------------------------------------------------------------------------
    // Raw HTTP utilities (work with 7.0.63 runtime, no new MinecraftClient deps)
    // -------------------------------------------------------------------------

    private String httpGet(String urlStr) throws IOException, MinecraftClientHttpException {
        LOGGER.info("[http] GET {}", urlStr);
        HttpURLConnection conn = openConnection(urlStr, proxy);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        int status = conn.getResponseCode();
        if (status >= 400) {
            String errBody = readFully(conn.getErrorStream());
            LOGGER.info("[http] GET {} -> {} | error-body: {}", urlStr, status, errBody);
            conn.disconnect();
            throw new MinecraftClientHttpException(status);
        }
        String body = readFully(conn.getInputStream());
        LOGGER.info("[http] GET {} -> {} | body: {}", urlStr, status, body);
        conn.disconnect();
        return body;
    }

    private String httpPost(String urlStr, String jsonBody) throws IOException, MinecraftClientHttpException {
        return httpSend("POST", urlStr, jsonBody);
    }

    private <T> T httpPut(String urlStr, String jsonBody, Class<T> responseType)
            throws IOException, MinecraftClientHttpException {
        String resp = httpSend("PUT", urlStr, jsonBody);
        if (resp == null || resp.isBlank() || responseType == Void.class) return null;
        return mapper.readValue(resp, responseType);
    }

    private String httpSend(String method, String urlStr, String jsonBody)
            throws IOException, MinecraftClientHttpException {
        LOGGER.info("[http] {} {} | request-body: {}", method, urlStr, jsonBody);
        HttpURLConnection conn = openConnection(urlStr, proxy);
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
            LOGGER.info("[http] {} {} -> {} | error-body: {}", method, urlStr, status, errBody);
            conn.disconnect();
            throw new MinecraftClientHttpException(status);
        }
        String body = readFully(conn.getInputStream());
        LOGGER.info("[http] {} {} -> {} | response-body: {}", method, urlStr, status, body);
        conn.disconnect();
        return body;
    }

    private static HttpURLConnection openConnection(String urlStr, Proxy proxy) throws IOException {
        URL url = new URL(urlStr);
        return (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
    }

    private static String readFully(InputStream is) throws IOException {
        if (is == null) return "";
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Inline helper types (avoid needing inner-class-of-existing-class tricks)
    // -------------------------------------------------------------------------

    private enum UpdateType { ADD, REMOVE }

    /** Minimal record mirroring FriendActionRequest, used only internally. */
    private record FriendActionRequest(String name, UUID profileId, UpdateType updateType) {
        static FriendActionRequest byId(UUID profileId, UpdateType type) {
            return new FriendActionRequest(null, profileId, type);
        }
        static FriendActionRequest byName(String name, UpdateType type) {
            return new FriendActionRequest(name, null, type);
        }

        /** Builds JSON manually to avoid passing null UUID to UUIDTypeAdapter (which does not handle null). */
        String toJson() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            if (name != null) {
                sb.append("\"name\":\"").append(name.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                first = false;
            }
            if (profileId != null) {
                if (!first) sb.append(",");
                sb.append("\"profileId\":\"").append(profileId.toString().replace("-", "")).append("\"");
                first = false;
            }
            if (!first) sb.append(",");
            sb.append("\"updateType\":\"").append(updateType.name()).append("\"");
            sb.append("}");
            return sb.toString();
        }
    }

    private String buildPresenceJson(String status, JoinInfoUpdate joinInfo) {
        // Use mapper (Gson) so: null fields are omitted, UUIDs serialized as undashed hex
        // Keep invites as-is (even empty Set → "invites":[]); only value=null is omitted by Gson default
        JoinInfoReq ji = joinInfo != null
                ? new JoinInfoReq(joinInfo.value(), joinInfo.invites() != null ? joinInfo.invites() : Set.of())
                : null;
        return mapper.writeValueAsString(new PresenceReq(status, ji));
    }

    private record PresenceReq(String status, JoinInfoReq joinInfo) {}
    private record JoinInfoReq(String value, Set<UUID> invites) {}

}

