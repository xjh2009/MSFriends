package dev.msf.friends.webrtc;

import dev.msf.friends.util.Logging;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * WebRTC native library loader (1.7.10 / Java 8 port).
 */
public final class WebRtcNativeLoader {
    private static final Logger LOGGER = Logging.get(WebRtcNativeLoader.class);
    private static volatile boolean loaded = false;

    private WebRtcNativeLoader() {}

    public static void ensureLoaded(Path cacheDir) {
        if (loaded) return;
        synchronized (WebRtcNativeLoader.class) {
            if (loaded) return;
            try {
                // Try loading from bundled natives first
                loadFromBundle();
                loaded = true;
                LOGGER.info("[WebRTC] Native library loaded successfully");
            } catch (Exception e) {
                LOGGER.warn("[WebRTC] Failed to load native library from bundle, trying cache dir: {}", e.getMessage());
                try {
                    extractToCacheAndLoad(cacheDir);
                    loaded = true;
                    LOGGER.info("[WebRTC] Native library loaded from cache");
                } catch (Exception e2) {
                    LOGGER.error("[WebRTC] Failed to load native library", e2);
                }
            }
        }
    }

    public static boolean isLoaded() { return loaded; }

    private static void loadFromBundle() throws ClassNotFoundException {
        // dev.onvoid.webrtc bundles natives; just try creating a factory
        Class.forName("dev.onvoid.webrtc.PeerConnectionFactory");
    }

    private static void extractToCacheAndLoad(Path cacheDir) throws IOException {
        File dir = cacheDir.toFile();
        if (!dir.exists()) dir.mkdirs();
        // Load the webrtc-java native library
        System.loadLibrary("webrtc-java");
    }
}
