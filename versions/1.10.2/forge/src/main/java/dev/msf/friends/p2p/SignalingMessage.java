package dev.msf.friends.p2p;

import com.google.gson.JsonObject;
import dev.onvoid.webrtc.RTCIceCandidate;

import java.util.UUID;

public final class SignalingMessage {
    private final Type type;
    private final String sessionId;
    private final String sdp;
    private final WebRtc.Candidate iceCandidate;
    private final UUID profileId;

    public SignalingMessage(Type type, String sessionId,
                            String sdp,
                            WebRtc.Candidate iceCandidate,
                            UUID profileId) {
        this.type = type;
        this.sessionId = sessionId;
        this.sdp = sdp;
        this.iceCandidate = iceCandidate;
        this.profileId = profileId;
    }

    public Type type() { return type; }
    public String sessionId() { return sessionId; }
    public String sdp() { return sdp; }
    public WebRtc.Candidate iceCandidate() { return iceCandidate; }
    public UUID profileId() { return profileId; }

    public enum Type {
        JOIN_REQUEST, JOIN_ACCEPTED, JOIN_REJECTED, INVITE_DECLINED,
        OFFER, ANSWER, ICE_CANDIDATE;

        public String getSerializedName() { return name(); }
        public static Type byName(String s) { return Type.valueOf(s); }
    }

    public interface Payload {}

    public interface FriendJoin extends Payload {
        String sessionId();

        final class Request implements FriendJoin {
            private final String sessionId;
            private final UUID profileId;
            public Request(String sessionId, UUID profileId) { this.sessionId = sessionId; this.profileId = profileId; }
            public String sessionId() { return sessionId; }
            public UUID profileId() { return profileId; }
        }
        final class Accepted implements FriendJoin {
            private final String sessionId;
            public Accepted(String sessionId) { this.sessionId = sessionId; }
            public String sessionId() { return sessionId; }
        }
        final class Rejected implements FriendJoin {
            private final String sessionId;
            public Rejected(String sessionId) { this.sessionId = sessionId; }
            public String sessionId() { return sessionId; }
        }
        final class InviteDeclined implements FriendJoin {
            public static final InviteDeclined INSTANCE = new InviteDeclined();
            private InviteDeclined() {}
            public String sessionId() { return ""; }
        }
    }

    public interface WebRtc extends Payload {
        String sessionId();

        final class Candidate {
            private final String candidate;
            private final String sdpMid;
            private final int sdpMLineIndex;
            public Candidate(String candidate, String sdpMid, int sdpMLineIndex) {
                this.candidate = candidate; this.sdpMid = sdpMid; this.sdpMLineIndex = sdpMLineIndex;
            }
            public String candidate() { return candidate; }
            public String sdpMid() { return sdpMid; }
            public int sdpMLineIndex() { return sdpMLineIndex; }

            public static Candidate from(RTCIceCandidate c) {
                return new Candidate(c.sdp, c.sdpMid, c.sdpMLineIndex);
            }
            public RTCIceCandidate toRtcIceCandidate() {
                return new RTCIceCandidate(sdpMid != null ? sdpMid : "0", sdpMLineIndex, candidate);
            }
            JsonObject toJson() {
                JsonObject o = new JsonObject();
                o.addProperty("candidate", candidate);
                if (sdpMid != null) o.addProperty("sdpMid", sdpMid);
                o.addProperty("sdpMLineIndex", sdpMLineIndex);
                return o;
            }
            static Candidate fromJson(JsonObject o) {
                String c = o.get("candidate").getAsString();
                String mid = o.has("sdpMid") && !o.get("sdpMid").isJsonNull() ? o.get("sdpMid").getAsString() : null;
                int idx = o.has("sdpMLineIndex") ? o.get("sdpMLineIndex").getAsInt() : 0;
                return new Candidate(c, mid, idx);
            }
        }

        final class Offer implements WebRtc {
            private final String sessionId; private final String sdp;
            public Offer(String sessionId, String sdp) { this.sessionId = sessionId; this.sdp = sdp; }
            public String sessionId() { return sessionId; }
            public String sdp() { return sdp; }
        }
        final class Answer implements WebRtc {
            private final String sessionId; private final String sdp;
            public Answer(String sessionId, String sdp) { this.sessionId = sessionId; this.sdp = sdp; }
            public String sessionId() { return sessionId; }
            public String sdp() { return sdp; }
        }
        final class IceCandidate implements WebRtc {
            private final String sessionId; private final Candidate candidate;
            public IceCandidate(String sessionId, Candidate candidate) { this.sessionId = sessionId; this.candidate = candidate; }
            public String sessionId() { return sessionId; }
            public Candidate candidate() { return candidate; }
        }
    }

    // factories
    public static SignalingMessage joinRequest(String sessionId) { return joinRequest(sessionId, null); }
    public static SignalingMessage joinRequest(String sessionId, UUID profileId) {
        return from(new FriendJoin.Request(sessionId, profileId));
    }
    public static SignalingMessage joinAccepted(String sessionId) { return from(new FriendJoin.Accepted(sessionId)); }
    public static SignalingMessage joinRejected(String sessionId) { return from(new FriendJoin.Rejected(sessionId)); }
    public static SignalingMessage inviteDeclined() { return from(FriendJoin.InviteDeclined.INSTANCE); }
    public static SignalingMessage offer(String sessionId, String sdp) { return from(new WebRtc.Offer(sessionId, sdp)); }
    public static SignalingMessage answer(String sessionId, String sdp) { return from(new WebRtc.Answer(sessionId, sdp)); }
    public static SignalingMessage iceCandidate(String sessionId, RTCIceCandidate c) {
        return from(new WebRtc.IceCandidate(sessionId, WebRtc.Candidate.from(c)));
    }

    private static SignalingMessage from(Payload payload) {
        if (payload instanceof FriendJoin.Request) {
            FriendJoin.Request r = (FriendJoin.Request) payload;
            return new SignalingMessage(Type.JOIN_REQUEST, r.sessionId(), null, null, r.profileId());
        } else if (payload instanceof FriendJoin.Accepted) {
            FriendJoin.Accepted a = (FriendJoin.Accepted) payload;
            return new SignalingMessage(Type.JOIN_ACCEPTED, a.sessionId(), null, null, null);
        } else if (payload instanceof FriendJoin.Rejected) {
            FriendJoin.Rejected r = (FriendJoin.Rejected) payload;
            return new SignalingMessage(Type.JOIN_REJECTED, r.sessionId(), null, null, null);
        } else if (payload instanceof FriendJoin.InviteDeclined) {
            return new SignalingMessage(Type.INVITE_DECLINED, UUID.randomUUID().toString(), null, null, null);
        } else if (payload instanceof WebRtc.Offer) {
            WebRtc.Offer o = (WebRtc.Offer) payload;
            return new SignalingMessage(Type.OFFER, o.sessionId(), o.sdp(), null, null);
        } else if (payload instanceof WebRtc.Answer) {
            WebRtc.Answer a = (WebRtc.Answer) payload;
            return new SignalingMessage(Type.ANSWER, a.sessionId(), a.sdp(), null, null);
        } else if (payload instanceof WebRtc.IceCandidate) {
            WebRtc.IceCandidate ic = (WebRtc.IceCandidate) payload;
            return new SignalingMessage(Type.ICE_CANDIDATE, ic.sessionId(), null, ic.candidate(), null);
        } else {
            throw new IllegalArgumentException("Unknown payload: " + payload);
        }
    }

    public Payload decode() {
        switch (type) {
            case JOIN_REQUEST:  return new FriendJoin.Request(sessionId, profileId);
            case JOIN_ACCEPTED: return new FriendJoin.Accepted(sessionId);
            case JOIN_REJECTED: return new FriendJoin.Rejected(sessionId);
            case INVITE_DECLINED: return FriendJoin.InviteDeclined.INSTANCE;
            case OFFER:  return sdp != null ? new WebRtc.Offer(sessionId, sdp) : null;
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
        String sdp = (o.has("sdp") && !o.get("sdp").isJsonNull()) ? o.get("sdp").getAsString() : null;
        WebRtc.Candidate cand = (o.has("iceCandidate") && !o.get("iceCandidate").isJsonNull())
                ? WebRtc.Candidate.fromJson(o.getAsJsonObject("iceCandidate"))
                : null;
        UUID profileId = (o.has("profileId") && !o.get("profileId").isJsonNull())
                ? UUID.fromString(o.get("profileId").getAsString())
                : null;
        return new SignalingMessage(t, sid, sdp, cand, profileId);
    }
}
