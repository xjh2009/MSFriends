package com.mojang.authlib.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.*;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HTTP implementation of FriendsService. Java 8 compatible (no HttpClient).
 * Uses HttpURLConnection instead of java.net.http.
 */
public class YggdrasilFriendsService implements FriendsService {
    private static final Logger LOGGER = Logging.get();
    private static final Gson GSON = new GsonBuilder().create();

    private final String accessToken;
    private final Proxy proxy;
    private final String friendsEndpoint;
    private final String attributesEndpoint;
    private final String presenceEndpoint;

    public YggdrasilFriendsService(String accessToken, Proxy proxy,
                                    String friendsEndpoint, String attributesEndpoint,
                                    String presenceEndpoint) {
        this.accessToken = accessToken;
        this.proxy = proxy;
        this.friendsEndpoint = friendsEndpoint;
        this.attributesEndpoint = attributesEndpoint;
        this.presenceEndpoint = presenceEndpoint;
    }

    @Override
    public ResultCode sendFriendRequest(String name) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", name);
            int code = doPost(friendsEndpoint + "/request", body.toString());
            return mapCode(code);
        } catch (Exception e) {
            LOGGER.warn("[friends] sendFriendRequest(name) failed", e);
            return ResultCode.UNKNOWN_ERROR;
        }
    }

    @Override
    public ResultCode sendFriendRequest(UUID id) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("profileId", id.toString());
            int code = doPost(friendsEndpoint + "/request", body.toString());
            return mapCode(code);
        } catch (Exception e) {
            LOGGER.warn("[friends] sendFriendRequest(id) failed", e);
            return ResultCode.UNKNOWN_ERROR;
        }
    }

    @Override
    public ResultCode removeFriend(UUID id) {
        try {
            int code = doDelete(friendsEndpoint + "/" + id.toString());
            return mapCode(code);
        } catch (Exception e) {
            LOGGER.warn("[friends] removeFriend failed", e);
            return ResultCode.UNKNOWN_ERROR;
        }
    }

    @Override
    public ResultCode acceptIncomingFriendRequest(UUID id) {
        try {
            int code = doPost(friendsEndpoint + "/accept/" + id.toString(), "");
            return mapCode(code);
        } catch (Exception e) {
            LOGGER.warn("[friends] acceptIncoming failed", e);
            return ResultCode.UNKNOWN_ERROR;
        }
    }

    @Override
    public ResultCode declineIncomingFriendRequest(UUID id) {
        try {
            int code = doDelete(friendsEndpoint + "/incoming/" + id.toString());
            return mapCode(code);
        } catch (Exception e) {
            LOGGER.warn("[friends] declineIncoming failed", e);
            return ResultCode.UNKNOWN_ERROR;
        }
    }

    @Override
    public ResultCode revokeOutgoingFriendRequest(UUID id) {
        try {
            int code = doDelete(friendsEndpoint + "/outgoing/" + id.toString());
            return mapCode(code);
        } catch (Exception e) {
            LOGGER.warn("[friends] revokeOutgoing failed", e);
            return ResultCode.UNKNOWN_ERROR;
        }
    }

    @Override
    public ResultCode updateFriendSettings(boolean friendListEnabled, boolean allowRequests) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("friendListEnabled", friendListEnabled);
            body.addProperty("allowFriendRequests", allowRequests);
            int code = doPost(attributesEndpoint, body.toString());
            return mapCode(code);
        } catch (Exception e) {
            LOGGER.warn("[friends] updateSettings failed", e);
            return ResultCode.UNKNOWN_ERROR;
        }
    }

    @Override
    public FriendsListResponse getFriendList() {
        try {
            String json = doGet(friendsEndpoint);
            JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
            List<FriendDto> friends = parseFriendList(obj.getAsJsonArray("friends"));
            List<FriendDto> incoming = parseFriendList(obj.getAsJsonArray("incomingRequests"));
            List<FriendDto> outgoing = parseFriendList(obj.getAsJsonArray("outgoingRequests"));
            return new FriendsListResponse(friends, incoming, outgoing);
        } catch (Exception e) {
            LOGGER.warn("[friends] getFriendList failed", e);
            return new FriendsListResponse(
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList()
            );
        }
    }

    @Override
    public PresenceResponse getPresence() {
        try {
            String json = doGet(presenceEndpoint);
            JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("statuses");
            List<PresenceStatusDto> statuses = new ArrayList<>();
            if (arr != null) {
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    UUID pid = UUID.fromString(o.get("profileId").getAsString());
                    String name = o.has("name") ? o.get("name").getAsString() : "";
                    PresenceStatus status = PresenceStatus.byName(o.get("status").getAsString());
                    PresenceStatusDto.JoinInfo ji = null;
                    if (o.has("joinInfo") && !o.get("joinInfo").isJsonNull()) {
                        JsonObject jio = o.getAsJsonObject("joinInfo");
                        String val = jio.has("value") ? jio.get("value").getAsString() : "";
                        List<UUID> invites = new ArrayList<>();
                        if (jio.has("invites")) {
                            for (JsonElement ie : jio.getAsJsonArray("invites")) {
                                invites.add(UUID.fromString(ie.getAsString()));
                            }
                        }
                        ji = new PresenceStatusDto.JoinInfo(val, invites);
                    }
                    statuses.add(new PresenceStatusDto(pid, name, status, ji));
                }
            }
            return new PresenceResponse(statuses);
        } catch (Exception e) {
            LOGGER.warn("[friends] getPresence failed", e);
            return new PresenceResponse(new ArrayList<>());
        }
    }

    @Override
    public void updatePresence(JoinInfoUpdate update) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("value", update.value());
            JsonArray invites = new JsonArray();
            for (UUID id : update.invites()) {
                invites.add(id.toString());
            }
            body.add("invites", invites);
            doPost(presenceEndpoint, body.toString());
        } catch (Exception e) {
            LOGGER.warn("[friends] updatePresence failed", e);
        }
    }

    // --- HTTP helpers ---

    private List<FriendDto> parseFriendList(JsonArray arr) {
        List<FriendDto> list = new ArrayList<>();
        if (arr == null) return list;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            UUID pid = UUID.fromString(o.get("profileId").getAsString());
            String name = o.has("name") ? o.get("name").getAsString() : "";
            list.add(new FriendDto(pid, name));
        }
        return list;
    }

    private int doPost(String endpoint, String body) throws IOException {
        HttpURLConnection conn = openConnection(endpoint);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return conn.getResponseCode();
    }

    private int doDelete(String endpoint) throws IOException {
        HttpURLConnection conn = openConnection(endpoint);
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        return conn.getResponseCode();
    }

    private String doGet(String endpoint) throws IOException {
        HttpURLConnection conn = openConnection(endpoint);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private HttpURLConnection openConnection(String endpoint) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return conn;
    }

    private ResultCode mapCode(int httpCode) {
        switch (httpCode) {
            case 200: case 201: case 204: return ResultCode.SUCCESS;
            case 404: return ResultCode.NOT_FOUND;
            case 409: return ResultCode.ALREADY_EXISTS;
            case 429: return ResultCode.TOO_MANY_REQUESTS;
            default: return ResultCode.UNKNOWN_ERROR;
        }
    }
}
