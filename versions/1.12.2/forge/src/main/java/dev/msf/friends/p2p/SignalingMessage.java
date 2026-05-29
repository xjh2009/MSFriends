package dev.msf.friends.p2p;

import com.google.gson.JsonObject;
import dev.onvoid.webrtc.RTCIceCandidate;
import javax.annotation.Nullable;

import java.util.UUID;

/**
 * SignalingMessage - Java 8 compatible version.
 * Replaces records with classes, sealed interfaces with regular interfaces.
 */
public class SignalingMessage {
    private final Type type;
    private final String sessionId;
    @Nullable private final String sdp;
    @Nullable private final WebRtc.Candidate iceCandidate;
    @Nullable private final UUID profileId;

    public SignalingMessage(Type type, String sessionId, @Nullable String sdp,
                            @Nullable WebRtc.Candidate iceCandidate, @Nullable UUID profileId) {
        this.type = type;
        this.sessionId = sessionId;
        this.sdp = sdp;
        this.iceCandidate = iceCandidate;
        this.profileId = profileId;
    }

    public Type type() { return type; }
    public String sessionId() { return sessionId; }
    @Nullable public String sdp() { return sdp; }
    @Nullable public WebRtc.Candidate iceCandidate() { return iceCandidate; }
    @Nullable public UUID profileId() { return profileId; }

    public enum Type {
        JOIN_REQUEST, JOIN_ACCEPTED, JOIN_REJECTED, INVITE_DECLINED,
        OFFER, ANSWER, ICE_CANDIDATE;

        public String getSerializedName() { return name(); }
        public static Type byName(String s) { return Type.valueOf(s); }
    }

    // --- FriendJoin types ---

    public static abstract class FriendJoin implements Payload {
        public static class Request extends FriendJoin {
            private final String sessionId;
            @Nullable private final UUID profileId;
            public Request(String sessionId, @Nullable UUID profileId) {
                this.sessionId = sessionId; this.profileId = profileId;
            }
            public String sessionId() { return sessionId; }
            @Nullable public UUID profileId() { return profileId; }
        }
        public static class Accepted extends FriendJoin {
            private final String sessionId;
            public Accepted(String sessionId) { this.sessionId = sessionId; }
            public String sessionId() { return sessionId; }
        }
        public static class Rejected extends FriendJoin {
            private final String sessionId;
            public Rejected(String sessionId) { this.sessionId = sessionId; }
            public String sessionId() { return sessionId; }
        }
        public static class InviteDeclined extends FriendJoin {
            public static final InviteDeclined INSTANCE = new InviteDeclined();
        }
    }

    // --- WebRtc types ---

    public static abstract class WebRtc implements Payload {
        public abstract String sessionId();

        public static class Candidate {
            private final String candidate;
            @Nullable private final String sdpMid;
            private final int sdpMLineIndex;

            public Candidate(String candidate, @Nullable String sdpMid, int sdpMLineIndex) {
                this.candidate = candidate; this.sdpMid = sdpMid; this.sdpMLineIndex = sdpMLineIndex;
            }

            public static Candidate from(RTCIceCandidate c) {
                return new Candidate(c.sdp, c.sdpMid, c.sdpMLineIndex);
            }

            public RTCIceCandidate toRtcIceCandidate() {
                return new RTCIceCandidate(sdpMid != null ? sdpMid : "0", sdpMLineIndex, candidate);
            }

            public JsonObject toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("candidate", candidate);
                if (sdpMid != null) o.addProperty("sdpMid", sdpMid);
                o.addProperty("sdpMLineIndex", sdpMLineIndex);
                return o;
            }

            static Candidate fromJson(JsonObject o) {
                String c = o.get("candidate").getAsString();
                String mid = o.has("sdpMid") && !o.get("sdpMid").isJsonNull()
                        ? o.get("sdpMid").getAsString() : null;
                int idx = o.has("sdpMLineIndex") ? o.get("sdpMLineIndex").getAsInt() : 0;
                return new Candidate(c, mid, idx);
            }
        }

        public static class Offer extends WebRtc {
            private final String sessionId; private final String sdp;
            public Offer(String sessionId, String sdp) { this.sessionId = sessionId; this.sdp = sdp; }
            @Override public String sessionId() { return sessionId; }
            public String sdp() { return sdp; }
        }
        public static class Answer extends WebRtc {
            private final String sessionId; private final String sdp;
            public Answer(String sessionId, String sdp) { this.sessionId = sessionId; this.sdp = sdp; }
            @Override public String sessionId() { return sessionId; }
            public String sdp() { return sdp; }
        }
        public static class IceCandidate extends WebRtc {
            private final String sessionId; private final Candidate candidate;
            public IceCandidate(String sessionId, Candidate candidate) { this.sessionId = sessionId; this.candidate = candidate; }
            @Override public String sessionId() { return sessionId; }
            public Candidate candidate() { return candidate; }
        }
    }

    public interface Payload {}

    // --- Factory methods ---

    public static SignalingMessage joinRequest(String sessionId) { return joinRequest(sessionId, null); }
    public static SignalingMessage joinRequest(String sessionId, @Nullable UUID profileId) {
        return new SignalingMessage(Type.JOIN_REQUEST, sessionId, null, null, profileId);
    }
    public static SignalingMessage joinAccepted(String sessionId) {
        return new SignalingMessage(Type.JOIN_ACCEPTED, sessionId, null, null, null);
    }
    public static SignalingMessage joinRejected(String sessionId) {
        return new SignalingMessage(Type.JOIN_REJECTED, sessionId, null, null, null);
    }
    public static SignalingMessage inviteDeclined() {
        return new SignalingMessage(Type.INVITE_DECLINED, UUID.randomUUID().toString(), null, null, null);
    }
    public static SignalingMessage offer(String sessionId, String sdp) {
        return new SignalingMessage(Type.OFFER, sessionId, sdp, null, null);
    }
    public static SignalingMessage answer(String sessionId, String sdp) {
        return new SignalingMessage(Type.ANSWER, sessionId, sdp, null, null);
    }
    public static SignalingMessage iceCandidate(String sessionId, RTCIceCandidate c) {
        return new SignalingMessage(Type.ICE_CANDIDATE, sessionId, null, WebRtc.Candidate.from(c), null);
    }

    @Nullable
    public Payload decode() {
        switch (type) {
            case JOIN_REQUEST: return new FriendJoin.Request(sessionId, profileId);
            case JOIN_ACCEPTED: return new FriendJoin.Accepted(sessionId);
            case JOIN_REJECTED: return new FriendJoin.Rejected(sessionId);
            case INVITE_DECLINED: return FriendJoin.InviteDeclined.INSTANCE;
            case OFFER: return sdp != null ? new WebRtc.Offer(sessionId, sdp) : null;
            case ANSWER: return sdp != null ? new WebRtc.Answer(sessionId, sdp) : null;
            case ICE_CANDIDATE:
                if (iceCandidate != null) return new WebRtc.IceCandidate(sessionId, iceCandidate);
                if (sdp != null) return new WebRtc.IceCandidate(sessionId, new WebRtc.Candidate(sdp, "0", 0));
                return null;
            default: return null;
        }
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type.getSerializedName());
        o.addProperty("sessionId", sessionId);
        if (sdp != null) o.addProperty("sdp", sdp);
        if (iceCandidate != null) o.add("iceCandidate", iceCandidate.toJson());
        if (profileId != null) o.addProperty("profileId", profileId.toString());
        return o;
    }

    public static SignalingMessage fromJson(JsonObject o) {
        Type t = Type.byName(o.get("type").getAsString());
        String sid = o.get("sessionId").getAsString();
        String sdpVal = (o.has("sdp") && !o.get("sdp").isJsonNull()) ? o.get("sdp").getAsString() : null;
        WebRtc.Candidate cand = (o.has("iceCandidate") && !o.get("iceCandidate").isJsonNull())
                ? WebRtc.Candidate.fromJson(o.getAsJsonObject("iceCandidate")) : null;
        UUID pid = (o.has("profileId") && !o.get("profileId").isJsonNull())
                ? UUID.fromString(o.get("profileId").getAsString()) : null;
        return new SignalingMessage(t, sid, sdpVal, cand, pid);
    }
}
