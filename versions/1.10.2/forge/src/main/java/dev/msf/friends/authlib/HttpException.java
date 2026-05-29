package dev.msf.friends.authlib;

/**
 * Simple HTTP exception replacement for the authlib 7.0.72 MinecraftClientHttpException.
 * On 1.10.2, the bundled authlib does not have this class.
 */
public class HttpException extends RuntimeException {
    private final int status;

    public HttpException(int status) {
        super("HTTP " + status);
        this.status = status;
    }

    public int getStatus() { return status; }
}
