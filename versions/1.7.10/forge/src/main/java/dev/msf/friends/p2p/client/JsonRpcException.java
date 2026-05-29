package dev.msf.friends.p2p.client;

public class JsonRpcException extends Exception {
    private final int code;
    private final String serverMessage;
    private final String data;

    public JsonRpcException(int code, String serverMessage, String data) {
        super("JSON-RPC error " + code + ": " + serverMessage);
        this.code = code;
        this.serverMessage = serverMessage;
        this.data = data;
    }

    public int code() { return code; }
    public String serverMessage() { return serverMessage; }
    public String data() { return data; }
}
