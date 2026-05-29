package com.mojang.authlib.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.FriendData;
import com.mojang.authlib.yggdrasil.response.FriendDto;
import com.mojang.authlib.yggdrasil.response.FriendsListResponse;
import com.mojang.authlib.yggdrasil.response.PresenceResponse;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * HTTP implementation of FriendsService for authlib 1.5.21 (MC 1.7.10).
 * Uses HttpURLConnection since java.net.http does not exist in Java 8.
 */
public class YggdrasilFriendsService implements FriendsService {
    private static final Logger LOGGER = Logging.get(YggdrasilFriendsService.class);
    private static final Gson GSON = new Gson();
    private static final JsonParser JSON_PARSER = new JsonParser();

    private final String servicesHost;
    private final String accessToken;
    private final UUID profileId;

    public YggdrasilFriendsService(String servicesHost, String accessToken, UUID profileId) {
        this.servicesHost = servicesHost;
        this.accessToken = accessToken;
        this.profileId = profileId;
    }

    @Override
    public ResultCode getFriendData(FriendDataCallback callback) {
        try {
            String url = "https://" + servicesHost + "/friends";
            String response = doGet(url);
            if (response == null) {
                callback.accept(FriendData.empty());
                return ResultCode.CONNECTION_ISSUE;
            }
            FriendsListResponse list = GSON.fromJson(response, FriendsListResponse.class);
            Set<FriendDto> friends = new HashSet<>(list.getFriends());
            Set<FriendDto> incoming = new HashSet<>(list.getIncomingRequests());
            Set<FriendDto> outgoing = new HashSet<>(list.getOutgoingRequests());
            callback.accept(new FriendData(friends, incoming, outgoing));
            return ResultCode.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to get friend data", e);
            callback.accept(FriendData.empty());
            return ResultCode.GENERIC_ERROR;
        }
    }

    @Override
    public FriendData getFriendData() {
        final FriendData[] result = {FriendData.empty()};
        ResultCode code = getFriendData(new FriendDataCallback() {
            @Override public void accept(FriendData data) { result[0] = data; }
        });
        return result[0];
    }

    @Override
    public void addFriend(String name) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", name);
            doPost("https://" + servicesHost + "/friends", body.toString());
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to add friend: {}", name, e);
        }
    }

    @Override
    public ResultCode sendFriendRequest(String name) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", name);
            String response = doPost("https://" + servicesHost + "/friends", body.toString());
            return response != null ? ResultCode.SUCCESS : ResultCode.GENERIC_ERROR;
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to send friend request: {}", name, e);
            return ResultCode.GENERIC_ERROR;
        }
    }

    @Override
    public ResultCode sendFriendRequest(UUID profileId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("profileId", profileId.toString());
            String response = doPost("https://" + servicesHost + "/friends", body.toString());
            return response != null ? ResultCode.SUCCESS : ResultCode.GENERIC_ERROR;
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to send friend request", e);
            return ResultCode.GENERIC_ERROR;
        }
    }

    @Override
    public ResultCode acceptFriendRequest(UUID profileId) {
        try {
            doPut("https://" + servicesHost + "/friends/" + profileId.toString() + "/accept", "");
            return ResultCode.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to accept friend request", e);
            return ResultCode.GENERIC_ERROR;
        }
    }

    @Override
    public ResultCode declineFriendRequest(UUID profileId) {
        try {
            doDelete("https://" + servicesHost + "/friends/" + profileId.toString() + "/incoming");
            return ResultCode.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to decline friend request", e);
            return ResultCode.GENERIC_ERROR;
        }
    }

    @Override
    public ResultCode updateFriendSettings(boolean friendListEnabled, boolean allowFriendRequests) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("friendListEnabled", friendListEnabled);
            body.addProperty("allowFriendRequests", allowFriendRequests);
            doPut("https://" + servicesHost + "/player/attributes", body.toString());
            return ResultCode.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to update friend settings", e);
            return ResultCode.GENERIC_ERROR;
        }
    }

    @Override
    public ResultCode removeFriend(UUID profileId) {
        try {
            doDelete("https://" + servicesHost + "/friends/" + profileId.toString());
            return ResultCode.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to remove friend", e);
            return ResultCode.GENERIC_ERROR;
        }
    }

    @Override
    public void acceptRequest(UUID profileId) {
        try {
            doPut("https://" + servicesHost + "/friends/" + profileId.toString() + "/accept", "");
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to accept request", e);
        }
    }

    @Override
    public void declineRequest(UUID profileId) {
        try {
            doDelete("https://" + servicesHost + "/friends/" + profileId.toString() + "/incoming");
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to decline request", e);
        }
    }

    @Override
    public ResultCode blockPlayer(UUID profileId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("profileId", profileId.toString());
            doPost("https://" + servicesHost + "/friends/block", body.toString());
            return ResultCode.SUCCESS;
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to block player", e);
            return ResultCode.GENERIC_ERROR;
        }
    }

    @Override
    public ToggleValue getFriendInvitesToggle() {
        try {
            String url = "https://" + servicesHost + "/player/attributes";
            String response = doGet(url);
            if (response != null) {
                JsonObject obj = JSON_PARSER.parse(response).getAsJsonObject();
                if (obj.has("friendInvitesToggle")) {
                    return ToggleValue.fromString(obj.get("friendInvitesToggle").getAsString());
                }
            }
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to get friend invites toggle", e);
        }
        return ToggleValue.ENABLED;
    }

    @Override
    public void setFriendInvitesToggle(ToggleValue value) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("friendInvitesToggle", value.name().toLowerCase());
            doPut("https://" + servicesHost + "/player/attributes", body.toString());
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to set friend invites toggle", e);
        }
    }

    @Override
    public ChatToggleValue getChatToggle() {
        try {
            String url = "https://" + servicesHost + "/player/attributes";
            String response = doGet(url);
            if (response != null) {
                JsonObject obj = JSON_PARSER.parse(response).getAsJsonObject();
                if (obj.has("chatToggle")) {
                    return ChatToggleValue.fromString(obj.get("chatToggle").getAsString());
                }
            }
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to get chat toggle", e);
        }
        return ChatToggleValue.ENABLED;
    }

    @Override
    public void setChatToggle(ChatToggleValue value) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("chatToggle", value.name().toLowerCase());
            doPut("https://" + servicesHost + "/player/attributes", body.toString());
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to set chat toggle", e);
        }
    }

    @Override
    public PresenceResponse presence(String status, JoinInfoUpdate joinInfo) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("status", status);
            if (joinInfo != null) {
                if (joinInfo.value() != null) {
                    body.addProperty("value", joinInfo.value());
                }
                if (joinInfo.invites() != null && !joinInfo.invites().isEmpty()) {
                    JsonArray invitesArr = new JsonArray();
                    for (UUID id : joinInfo.invites()) {
                        invitesArr.add(new JsonPrimitive(id.toString()));
                    }
                    body.add("invites", invitesArr);
                }
            }
            String response = doPost("https://" + servicesHost + "/presence", body.toString());
            if (response != null) {
                JsonObject obj = JSON_PARSER.parse(response).getAsJsonObject();
                JsonArray presenceArr = obj.has("presence") ? obj.getAsJsonArray("presence") : new JsonArray();
                List<PresenceStatusDto> list = new ArrayList<>();
                for (JsonElement el : presenceArr) {
                    JsonObject p = el.getAsJsonObject();
                    UUID pid = UUID.fromString(p.get("profileId").getAsString());
                    UUID pmid = p.has("pmid") && !p.get("pmid").isJsonNull()
                            ? UUID.fromString(p.get("pmid").getAsString()) : null;
                    PresenceStatus pStatus = PresenceStatus.valueOf(p.get("status").getAsString());
                    PresenceStatusDto.JoinInfo ji = null;
                    if (p.has("joinInfo") && !p.get("joinInfo").isJsonNull()) {
                        JsonObject jiObj = p.getAsJsonObject("joinInfo");
                        String jiValue = jiObj.has("value") ? jiObj.get("value").getAsString() : null;
                        boolean invited = jiObj.has("invited") && jiObj.get("invited").getAsBoolean();
                        ji = new PresenceStatusDto.JoinInfo(jiValue, invited);
                    }
                    String lastUpdated = p.has("lastUpdated") ? p.get("lastUpdated").getAsString() : "";
                    list.add(new PresenceStatusDto(pid, pmid, pStatus, ji, lastUpdated));
                }
                return new PresenceResponse(list);
            }
        } catch (Exception e) {
            LOGGER.error("[FriendsService] Failed to update presence", e);
        }
        return new PresenceResponse(new ArrayList<PresenceStatusDto>());
    }

    // --- HTTP helpers ---

    private String doGet(String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(urlStr, "GET");
        return readResponse(conn);
    }

    private String doPost(String urlStr, String body) throws IOException {
        HttpURLConnection conn = openConnection(urlStr, "POST");
        conn.setDoOutput(true);
        writeBody(conn, body);
        return readResponse(conn);
    }

    private String doPut(String urlStr, String body) throws IOException {
        HttpURLConnection conn = openConnection(urlStr, "PUT");
        conn.setDoOutput(true);
        writeBody(conn, body);
        return readResponse(conn);
    }

    private void doDelete(String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(urlStr, "DELETE");
        readResponse(conn);
    }

    private HttpURLConnection openConnection(String urlStr, String method) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        return conn;
    }

    private void writeBody(HttpURLConnection conn, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        if (code >= 400) {
            LOGGER.warn("[FriendsService] HTTP {}: {}", code, sb.toString());
            return null;
        }
        return sb.toString();
    }
}
