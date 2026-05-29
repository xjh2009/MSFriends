package dev.msf.friends;

/**
 * Shared constants for the MSF Friends mod (1.7.10, Java 8).
 */
public final class MsfFriendsConstants {
    private MsfFriendsConstants() {}

    public static final String MOD_ID = "msf_friends";
    public static final String MOD_NAME = "Minecraft Service Friends (MSF)";

    // Polling intervals
    public static final long FRIEND_LIST_POLL_FOREGROUND_SECONDS = 60L;
    public static final long FRIEND_LIST_POLL_BACKGROUND_SECONDS = 300L;
    public static final long PRESENCE_UPDATE_INTERVAL_SECONDS = 10L;
    public static final long MAX_PRESENCE_UPDATE_INTERVAL_SECONDS = 60L;

    // Signaling
    public static final long PING_INTERVAL_SECONDS = 50L;
    public static final String SIGNALING_ENV_PROP = "msf.signaling.env";

    // RPC methods
    public static final String RPC_PING = "System_Ping_v1_0";
    public static final String RPC_PONG = "System_Pong_v1_0";
    public static final String RPC_TURN_AUTH = "Signaling_TurnAuth_v1_0";
    public static final String RPC_SEND_CLIENT = "Signaling_SendClientMessage_v1_0";
    public static final String RPC_RECEIVE = "Signaling_ReceiveMessage_v1_0";

    // DataChannel
    public static final String DATA_CHANNEL_NAME = "minecraft";
    public static final int MAX_CHUNK_SIZE = 262144;        // 256 KiB
    public static final long HIGH_WATER_MARK = 1048576L;    // 1 MiB
    public static final long LOW_WATER_MARK = 262144L;      // 256 KiB

    // Handshake
    public static final long PENDING_HANDSHAKE_TIMEOUT_SECONDS = 10L;
    public static final long JOIN_REQUEST_TIMEOUT_MINUTES = 1L;
}
