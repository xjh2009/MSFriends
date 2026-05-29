package dev.msf.friends.p2p;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.UUID;

/**
 * Signaling message types (Java 8 — no sealed interfaces, uses class hierarchy).
 */
public abstract class SignalingMessage {
    public abstract String type();
    public abstract JsonElement toJson();

    // FriendJoin hierarchy
    public static abstract class FriendJoin extends SignalingMessage {
        public static class Request extends FriendJoin {
            private final String sessionId;
            private final UUID profileId;
            public Request(String sessionId, UUID profileId) { this.sessionId = sessionId; this.profileId = profileId; }
            public String sessionId() { return sessionId; }
            public UUID profileId() { return profileId; }
            @Override public String type() { return "friend_join_request"; }
            @Override public JsonElement toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("type", type());
                o.addProperty("sessionId", sessionId);
                o.addProperty("profileId", profileId.toString());
                return o;
            }
        }
        public static class Accepted extends FriendJoin {
            private final String sessionId;
            public Accepted(String sessionId) { this.sessionId = sessionId; }
            public String sessionId() { return sessionId; }
            @Override public String type() { return "friend_join_accepted"; }
            @Override public JsonElement toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("type", type());
                o.addProperty("sessionId", sessionId);
                return o;
            }
        }
        public static class Rejected extends FriendJoin {
            private final String sessionId;
            public Rejected(String sessionId) { this.sessionId = sessionId; }
            public String sessionId() { return sessionId; }
            @Override public String type() { return "friend_join_rejected"; }
            @Override public JsonElement toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("type", type());
                o.addProperty("sessionId", sessionId);
                return o;
            }
        }
        public static class InviteDeclined extends FriendJoin {
            @Override public String type() { return "friend_invite_declined"; }
            @Override public JsonElement toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("type", type());
                return o;
            }
        }
    }

    // WebRtc hierarchy
    public static abstract class WebRtc extends SignalingMessage {
        public static class Offer extends WebRtc {
            private final String sessionId;
            private final String sdp;
            public Offer(String sessionId, String sdp) { this.sessionId = sessionId; this.sdp = sdp; }
            public String sessionId() { return sessionId; }
            public String sdp() { return sdp; }
            @Override public String type() { return "webrtc_offer"; }
            @Override public JsonElement toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("type", type());
                o.addProperty("sessionId", sessionId);
                o.addProperty("sdp", sdp);
                return o;
            }
        }
        public static class Answer extends WebRtc {
            private final String sessionId;
            private final String sdp;
            public Answer(String sessionId, String sdp) { this.sessionId = sessionId; this.sdp = sdp; }
            public String sessionId() { return sessionId; }
            public String sdp() { return sdp; }
            @Override public String type() { return "webrtc_answer"; }
            @Override public JsonElement toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("type", type());
                o.addProperty("sessionId", sessionId);
                o.addProperty("sdp", sdp);
                return o;
            }
        }
        public static class IceCandidate extends WebRtc {
            private final String sessionId;
            private final IceCandidateData candidate;
            public IceCandidate(String sessionId, IceCandidateData candidate) { this.sessionId = sessionId; this.candidate = candidate; }
            public String sessionId() { return sessionId; }
            public IceCandidateData candidate() { return candidate; }
            @Override public String type() { return "webrtc_ice_candidate"; }
            @Override public JsonElement toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("type", type());
                o.addProperty("sessionId", sessionId);
                o.add("candidate", candidate.toJson());
                return o;
            }
        }
    }

    public static SignalingMessage fromJson(JsonObject obj) {
        String type = obj.get("type").getAsString();
        switch (type) {
            case "friend_join_request": return new FriendJoin.Request(obj.get("sessionId").getAsString(), UUID.fromString(obj.get("profileId").getAsString()));
            case "friend_join_accepted": return new FriendJoin.Accepted(obj.get("sessionId").getAsString());
            case "friend_join_rejected": return new FriendJoin.Rejected(obj.get("sessionId").getAsString());
            case "friend_invite_declined": return new FriendJoin.InviteDeclined();
            case "webrtc_offer": return new WebRtc.Offer(obj.get("sessionId").getAsString(), obj.get("sdp").getAsString());
            case "webrtc_answer": return new WebRtc.Answer(obj.get("sessionId").getAsString(), obj.get("sdp").getAsString());
            case "webrtc_ice_candidate": return new WebRtc.IceCandidate(obj.get("sessionId").getAsString(), IceCandidateData.fromJson(obj.getAsJsonObject("candidate")));
            default: throw new IllegalArgumentException("Unknown signaling type: " + type);
        }
    }

    // Factory methods
    public static SignalingMessage joinRequest(String sessionId, UUID profileId) {
        return new FriendJoin.Request(sessionId, profileId);
    }
    public static SignalingMessage joinAccepted(String sessionId) {
        return new FriendJoin.Accepted(sessionId);
    }
    public static SignalingMessage joinRejected(String sessionId) {
        return new FriendJoin.Rejected(sessionId);
    }
    public static SignalingMessage inviteDeclined() {
        return new FriendJoin.InviteDeclined();
    }
    public static SignalingMessage offer(String sessionId, String sdp) {
        return new WebRtc.Offer(sessionId, sdp);
    }
    public static SignalingMessage answer(String sessionId, String sdp) {
        return new WebRtc.Answer(sessionId, sdp);
    }
    public static SignalingMessage iceCandidate(String sessionId, dev.onvoid.webrtc.RTCIceCandidate rtcCandidate) {
        return new WebRtc.IceCandidate(sessionId, IceCandidateData.fromRtc(rtcCandidate));
    }
}
