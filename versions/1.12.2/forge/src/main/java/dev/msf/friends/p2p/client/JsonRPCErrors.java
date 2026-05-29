package dev.msf.friends.p2p.client;

/**
 * JSON-RPC error codes.
 */
public final class JsonRPCErrors {
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    // Server-defined errors
    public static final int NOT_REGISTERED = -32000;
    public static final int PLAYER_UNREACHABLE = -32001;
    public static final int TURN_AUTH_FAILED = -32002;

    private JsonRPCErrors() {}
}
