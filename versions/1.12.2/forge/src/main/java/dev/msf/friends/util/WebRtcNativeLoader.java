package dev.msf.friends.util;

import org.apache.logging.log4j.Logger;

/**
 * WebRTC native library loader. Java 8 compatible.
 */
public final class WebRtcNativeLoader {
    private static final Logger LOGGER = Logging.get();
    private static volatile boolean loaded = false;

    private WebRtcNativeLoader() {}

    public static void ensureLoaded(java.nio.file.Path cacheDir) {
        if (loaded) return;
        synchronized (WebRtcNativeLoader.class) {
            if (loaded) return;
            try {
                LOGGER.info("[webrtc] Loading native library from {}", cacheDir);
                // The webrtc-java library handles native loading internally
                // Just try to load the class to trigger static init
                Class.forName("dev.onvoid.webrtc.RTCPeerConnection");
                loaded = true;
                LOGGER.info("[webrtc] Native library loaded successfully");
            } catch (Throwable t) {
                LOGGER.warn("[webrtc] Failed to load native library", t);
            }
        }
    }
}
