package dev.msf.friends.p2p;

import com.google.gson.JsonObject;
import dev.onvoid.webrtc.RTCIceCandidate;

/**
 * ICE candidate data, bridging between signaling JSON and RTC types.
 */
public class IceCandidateData {
    private final String candidate;
    private final String sdpMid;
    private final int sdpMLineIndex;

    public IceCandidateData(String candidate, String sdpMid, int sdpMLineIndex) {
        this.candidate = candidate;
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
    }

    public String candidate() { return candidate; }
    public String sdpMid() { return sdpMid; }
    public int sdpMLineIndex() { return sdpMLineIndex; }

    public RTCIceCandidate toRtcIceCandidate() {
        return new RTCIceCandidate(sdpMid, sdpMLineIndex, candidate);
    }

    public static IceCandidateData fromRtc(RTCIceCandidate c) {
        return new IceCandidateData(c.sdp, c.sdpMid, c.sdpMLineIndex);
    }

    public static IceCandidateData fromJson(JsonObject obj) {
        String cand = obj.has("candidate") ? obj.get("candidate").getAsString() : "";
        String mid = obj.has("sdpMid") ? obj.get("sdpMid").getAsString() : "";
        int idx = obj.has("sdpMLineIndex") ? obj.get("sdpMLineIndex").getAsInt() : 0;
        return new IceCandidateData(cand, mid, idx);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("candidate", candidate);
        o.addProperty("sdpMid", sdpMid);
        o.addProperty("sdpMLineIndex", sdpMLineIndex);
        return o;
    }
}
