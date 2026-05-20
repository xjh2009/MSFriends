package dev.msf.friends.util;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Ensures the webrtc-java native library is loaded before any WebRTC class is used.
 *
 * <p>On Windows x86_64 the native DLL is bundled in the mod JAR as a classpath resource;
 * NativeLoader picks it up automatically and this class does nothing.
 * On other platforms (Linux, macOS) the platform-specific native JAR is downloaded from
 * Mojang's libraries CDN on first launch, cached locally, and loaded via System.load().
 * NativeLoader's internal LOADED_LIB_SET is then updated via reflection so that webrtc-java's
 * own initialisation code sees the library as already loaded and skips its resource search.
 */
public final class WebRtcNativeLoader {

    private static final Logger LOGGER = Logging.get();
    private static final String BASE_URL =
            "https://libraries.minecraft.net/dev/onvoid/webrtc/webrtc-java/0.14.0/";
    private static final String LIB_NAME    = "webrtc-java";
    private static final String LIB_VERSION = "0.14.0";

    private static volatile boolean ensured = false;

    private WebRtcNativeLoader() {}

    /**
     * Call this once, before any WebRTC class is instantiated.
     *
     * @param cacheDir directory used to cache the downloaded native JAR / extracted binary
     *                 (e.g. {@code gameDir.resolve("libraries/dev/onvoid/webrtc/webrtc-java/0.14.0")})
     */
    public static synchronized void ensureLoaded(Path cacheDir) {
        if (ensured) return;
        ensured = true;

        // If the lib is already in NativeLoader's set (e.g. loaded by a previous run or
        // because the launcher pre-placed the native jar on the classpath), nothing to do.
        if (isRegisteredInNativeLoader()) {
            LOGGER.debug("[webrtc] NativeLoader already has {} registered", LIB_NAME);
            return;
        }

        String os   = getOsFamily();
        String arch = getArch();
        String classifier  = os + "-" + arch;
        // System.mapLibraryName("webrtc-java-windows-x86_64") -> "webrtc-java-windows-x86_64.dll"
        String nativeName  = System.mapLibraryName(LIB_NAME + "-" + classifier);

        // Windows: DLL is already bundled in the mod JAR as a classpath resource.
        // Let NativeLoader handle it; we only need to intervene on other platforms.
        if (isBundledInClasspath(nativeName)) {
            LOGGER.debug("[webrtc] {} is bundled in classpath; NativeLoader will handle it", nativeName);
            return;
        }

        // Non-Windows path: download the native jar from Mojang CDN, extract, load.
        String jarName = "webrtc-java-" + LIB_VERSION + "-" + classifier + ".jar";
        Path cacheJar    = cacheDir.resolve(jarName);
        Path nativeFile  = cacheDir.resolve(nativeName);

        try {
            Files.createDirectories(cacheDir);

            if (!Files.exists(nativeFile)) {
                if (!Files.exists(cacheJar)) {
                    LOGGER.info("[webrtc] Downloading {} for {} ...", jarName, classifier);
                    downloadFile(BASE_URL + jarName, cacheJar);
                    LOGGER.info("[webrtc] Download complete");
                }
                LOGGER.info("[webrtc] Extracting {} ...", nativeName);
                extractFromJar(cacheJar, nativeName, nativeFile);
            }

            LOGGER.info("[webrtc] Loading native from {}", nativeFile);
            System.load(nativeFile.toAbsolutePath().toString());
            registerInNativeLoader();
            LOGGER.info("[webrtc] Native loaded successfully for {}", classifier);

        } catch (Exception e) {
            LOGGER.error("[webrtc] Failed to prepare native for {}; P2P will not work", classifier, e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isBundledInClasspath(String resourceName) {
        try (InputStream s = WebRtcNativeLoader.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            return s != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isRegisteredInNativeLoader() {
        try {
            Set<String> set = getNativeLoaderSet();
            return set != null && set.contains(LIB_NAME);
        } catch (Exception e) {
            return false;
        }
    }

    private static void registerInNativeLoader() {
        try {
            Set<String> set = getNativeLoaderSet();
            if (set != null) set.add(LIB_NAME);
        } catch (Exception e) {
            LOGGER.warn("[webrtc] Could not register in NativeLoader.LOADED_LIB_SET: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getNativeLoaderSet() throws Exception {
        Class<?> cls = Class.forName("dev.onvoid.webrtc.internal.NativeLoader");
        Field f = cls.getDeclaredField("LOADED_LIB_SET");
        f.setAccessible(true);
        return (Set<String>) f.get(null);
    }

    private static String getOsFamily() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win"))                      return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        return "linux";
    }

    private static String getArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.equals("amd64") || arch.equals("x86_64")) return "x86_64";
        if (arch.equals("aarch64") || arch.equals("arm64")) return "aarch64";
        if (arch.contains("arm"))                           return "aarch32";
        return "x86_64";
    }

    private static void downloadFile(String urlStr, Path target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("User-Agent", "msf-friends-mod/1.0");
        int status = conn.getResponseCode();
        if (status != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + status + " downloading " + urlStr);
        }
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }

    private static void extractFromJar(Path jar, String entryName, Path target) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
                zis.closeEntry();
            }
        }
        throw new IOException("Entry '" + entryName + "' not found in " + jar);
    }
}
