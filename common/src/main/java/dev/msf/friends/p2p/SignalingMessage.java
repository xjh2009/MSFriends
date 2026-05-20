package dev.msf.friends.p2p;

import com.google.gson.JsonObject;
import dev.onvoid.webrtc.RTCIceCandidate;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Strict 26.2 port of {@code net.minecraft.client.multiplayer.p2p.SignalingMessage}.
 *
 * <p>Field names and decode semantics — including the fallback that turns a
 * legacy {@code (sdp,"0",0)} ICE candidate into a proper {@link WebRtc.Candidate}
 * — are preserved verbatim; the only deliberate changes are Gson serialization
 * and carrying the sender profileId on JOIN_REQUEST so the host can resolve
 * friend identity before presence catches up.
 */
public record SignalingMessage(Type type, String sessionId,
                               @Nullable String sdp,
                               WebRtc.@Nullable Candidate iceCandidate,
                               @Nullable UUID profileId) {

    public enum Type {
        JOIN_REQUEST, JOIN_ACCEPTED, JOIN_REJECTED, INVITE_DECLINED,
        OFFER, ANSWER, ICE_CANDIDATE;

        public String getSerializedName() { return name(); }
        public static Type byName(String s) { return Type.valueOf(s); }
    }

    public sealed interface Payload permits FriendJoin, WebRtc {}

    public sealed interface FriendJoin extends Payload
            permits FriendJoin.Request, FriendJoin.Accepted, FriendJoin.Rejected, FriendJoin.InviteDeclined {

        record Request(String sessionId, @Nullable UUID profileId) implements FriendJoin {}
        record Accepted(String sessionId) implements FriendJoin {}
        record Rejected(String sessionId) implements FriendJoin {}
        record InviteDeclined()           implements FriendJoin {
            public static final InviteDeclined INSTANCE = new InviteDeclined();
        }
    }

    public sealed interface WebRtc extends Payload
            permits WebRtc.Offer, WebRtc.Answer, WebRtc.IceCandidate {

        String sessionId();

        record Candidate(String candidate, @Nullable String sdpMid, int sdpMLineIndex) {

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
                String mid = o.has("sdpMid") && !o.get("sdpMid").isJsonNull()
                        ? o.get("sdpMid").getAsString() : null;
                int idx = o.has("sdpMLineIndex") ? o.get("sdpMLineIndex").getAsInt() : 0;
                return new Candidate(c, mid, idx);
            }
        }

        record Offer(String sessionId, String sdp) implements WebRtc {}
        record Answer(String sessionId, String sdp) implements WebRtc {}
        record IceCandidate(String sessionId, Candidate candidate) implements WebRtc {}
    }

    // -------- factories --------

    public static SignalingMessage joinRequest(String sessionId)  { return joinRequest(sessionId, null); }
    public static SignalingMessage joinRequest(String sessionId, @Nullable UUID profileId) {
        return from(new FriendJoin.Request(sessionId, profileId));
    }
    public static SignalingMessage joinAccepted(String sessionId) { return from(new FriendJoin.Accepted(sessionId)); }
    public static SignalingMessage joinRejected(String sessionId) { return from(new FriendJoin.Rejected(sessionId)); }
    public static SignalingMessage inviteDeclined()               { return from(FriendJoin.InviteDeclined.INSTANCE); }
    public static SignalingMessage offer(String sessionId, String sdp)  { return from(new WebRtc.Offer(sessionId, sdp)); }
    public static SignalingMessage answer(String sessionId, String sdp) { return from(new WebRtc.Answer(sessionId, sdp)); }

    public static SignalingMessage iceCandidate(String sessionId, RTCIceCandidate c) {
        return from(new WebRtc.IceCandidate(sessionId, WebRtc.Candidate.from(c)));
    }

    private static SignalingMessage from(Payload payload) {
        return switch (payload) {
            case FriendJoin.Request   r -> new SignalingMessage(Type.JOIN_REQUEST,  r.sessionId(), null, null, r.profileId());
            case FriendJoin.Accepted  a -> new SignalingMessage(Type.JOIN_ACCEPTED, a.sessionId(), null, null, null);
            case FriendJoin.Rejected  r -> new SignalingMessage(Type.JOIN_REJECTED, r.sessionId(), null, null, null);
            case FriendJoin.InviteDeclined ignored ->
                new SignalingMessage(Type.INVITE_DECLINED, UUID.randomUUID().toString(), null, null, null);
            case WebRtc.Offer  o -> new SignalingMessage(Type.OFFER,  o.sessionId(), o.sdp(), null, null);
            case WebRtc.Answer a -> new SignalingMessage(Type.ANSWER, a.sessionId(), a.sdp(), null, null);
            case WebRtc.IceCandidate ic ->
                new SignalingMessage(Type.ICE_CANDIDATE, ic.sessionId(), null, ic.candidate(), null);
        };
    }

    /**
     * Mirror of 26.2's {@code decode()}: returns {@code null} when an OFFER/ANSWER
     * is missing its SDP, or an ICE_CANDIDATE has neither candidate object nor
     * legacy {@code sdp} fallback.
     */
    @Nullable
    public Payload decode() {
        return switch (type) {
            case JOIN_REQUEST    -> new FriendJoin.Request(sessionId, profileId);
            case JOIN_ACCEPTED   -> new FriendJoin.Accepted(sessionId);
            case JOIN_REJECTED   -> new FriendJoin.Rejected(sessionId);
            case INVITE_DECLINED -> FriendJoin.InviteDeclined.INSTANCE;
            case OFFER           -> sdp != null ? new WebRtc.Offer(sessionId, sdp) : null;
            case ANSWER          -> sdp != null ? new WebRtc.Answer(sessionId, sdp) : null;
            case ICE_CANDIDATE   -> iceCandidate != null
                    ? new WebRtc.IceCandidate(sessionId, iceCandidate)
                    : (sdp != null
                        ? new WebRtc.IceCandidate(sessionId, new WebRtc.Candidate(sdp, "0", 0))
                        : null);
        };
    }

    // -------- gson wire format --------

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
