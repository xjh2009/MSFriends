package dev.msf.friends;

/** Mod-wide constants. */
public final class MsfFriendsConstants {
    public static final String MOD_ID = "msf_friends";
    public static final String LOGGER_NAME = "MSF/Friends";

    /** Signaling environment override property (mirrors Mojang 26.2 "signaling.environment"). */
    public static final String SIGNALING_ENV_PROP = "signaling.environment";

    /** Foreground / background friend-list poll intervals (mirrors 26.2). */
    public static final long FOREGROUND_POLL_NANOS  = 8_000_000_000L;    //  8 seconds
    public static final long BACKGROUND_POLL_NANOS  = 16_000_000_000L;  // 5 minutes
    public static final long POLL_TICK_SECONDS      = 1L;

    /** Presence broadcast interval (mirrors 26.2 PresenceHandler). */
    public static final long PRESENCE_INTERVAL_SECONDS     = 10L;
    public static final long PRESENCE_MAX_INTERVAL_SECONDS = 60L;

    /** Friend join request timeout (mirrors 26.2). */
    public static final long JOIN_REQUEST_TIMEOUT_MINUTES = 1L;

    /** Signaling reconnect delay (mirrors 26.2). */
    public static final long SIGNALING_RECONNECT_DELAY_SECONDS = 1L;

    /** JSON-RPC method names (mirrors 26.2 RpcMethods). */
    public static final String RPC_PING          = "System_Ping_v1_0";
    public static final String RPC_PONG          = "System_Pong_v1_0";
    public static final String RPC_TURN_AUTH     = "Signaling_TurnAuth_v1_0";
    public static final String RPC_SEND_CLIENT   = "Signaling_SendClientMessage_v1_0";
    public static final String RPC_RECEIVE       = "Signaling_ReceiveMessage_v1_0";

    public static final int  RPC_MAX_MESSAGE_BYTES = 65536;
    public static final long RPC_PING_INTERVAL_SECONDS = 50L;
    public static final String RPC_AUTH_HEADER  = "x-mojangauth";
    public static final String RPC_SESSION_HEADER = "Session-Id";
    public static final String CONFIGURATION_ENDPOINT = "/api/v1.0/configuration/java";

    private MsfFriendsConstants() {}
}
