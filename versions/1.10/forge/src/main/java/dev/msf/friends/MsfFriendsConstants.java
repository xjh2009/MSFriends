package dev.msf.friends;

/**
 * Mod-wide constants shared across loader modules.
 * Mirror of the root :common MsfFriendsConstants — kept here because
 * the root common module targets Java 17+ (records / sealed types)
 * which cannot be used in a Forge 1.10 (Java 8) build.
 */
public final class MsfFriendsConstants {
    public static final String MOD_ID = "msf_friends";
    public static final String MOD_NAME = "Minecraft Service Friends (MSF)";

    // ── Presence polling ──────────────────────────────────────────────
    public static final long PRESENCE_POLL_FOREGROUND_MS = 60_000;
    public static final long PRESENCE_POLL_BACKGROUND_MS = 300_000;

    // ── Signaling / JSON-RPC ──────────────────────────────────────────
    public static final String JSONRPC_VERSION = "2.0";
    public static final String METHOD_FRIEND_JOIN_REQUEST = "friendJoin.request";
    public static final String METHOD_FRIEND_JOIN_ACCEPTED = "friendJoin.accepted";
    public static final String METHOD_FRIEND_JOIN_REJECTED = "friendJoin.rejected";
    public static final String METHOD_FRIEND_JOIN_INVITE_DECLINED = "friendJoin.inviteDeclined";
    public static final String METHOD_WEBRTC_OFFER = "webrtc.offer";
    public static final String METHOD_WEBRTC_ANSWER = "webrtc.answer";
    public static final String METHOD_WEBRTC_ICE_CANDIDATE = "webrtc.iceCandidate";

    // ── Signaling server config ───────────────────────────────────────
    public static final String SIGNALING_HOST = "api.minecraftservices.com";
    public static final int SIGNALING_PORT = 443;
    public static final String SIGNALING_PATH = "/friends/signaling";

    private MsfFriendsConstants() {}
}
