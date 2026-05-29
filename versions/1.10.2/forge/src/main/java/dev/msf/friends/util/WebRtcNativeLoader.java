package dev.msf.friends.util;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class WebRtcNativeLoader {
    private static final Logger LOGGER = Logging.get();
    private static final String BASE_URL = "https://libraries.minecraft.net/dev/onvoid/webrtc/webrtc-java/0.14.0/";
    private static final String LIB_NAME = "webrtc-java";
    private static final String LIB_VERSION = "0.14.0";
    private static volatile boolean ensured = false;

    private WebRtcNativeLoader() {}

    public static synchronized void ensureLoaded(Path cacheDir) {
        if (ensured) return;
        ensured = true;

        if (isRegisteredInNativeLoader()) {
            LOGGER.debug("[webrtc] NativeLoader already has {} registered", LIB_NAME);
            return;
        }

        String os = getOsFamily();
        String arch = getArch();
        String classifier = os + "-" + arch;
        String nativeName = System.mapLibraryName(LIB_NAME + "-" + classifier);

        if (isBundledInClasspath(nativeName)) {
            LOGGER.debug("[webrtc] {} bundled in classpath", nativeName);
            return;
        }

        String jarName = "webrtc-java-" + LIB_VERSION + "-" + classifier + ".jar";
        Path cacheJar = cacheDir.resolve(jarName);
        Path nativeFile = cacheDir.resolve(nativeName);

        try {
            Files.createDirectories(cacheDir);
            if (!Files.exists(nativeFile)) {
                if (!Files.exists(cacheJar)) {
                    LOGGER.info("[webrtc] Downloading {} for {} ...", jarName, classifier);
                    downloadFile(BASE_URL + jarName, cacheJar);
                }
                LOGGER.info("[webrtc] Extracting {} ...", nativeName);
                extractFromJar(cacheJar, nativeName, nativeFile);
            }
            LOGGER.info("[webrtc] Loading native from {}", nativeFile);
            System.load(nativeFile.toAbsolutePath().toString());
            registerInNativeLoader();
            LOGGER.info("[webrtc] Native loaded for {}", classifier);
        } catch (Exception e) {
            LOGGER.error("[webrtc] Failed to prepare native for {}", classifier, e);
        }
    }

    private static boolean isBundledInClasspath(String resourceName) {
        try (InputStream s = WebRtcNativeLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            return s != null;
        } catch (IOException e) { return false; }
    }

    private static boolean isRegisteredInNativeLoader() {
        try {
            Set<String> set = getNativeLoaderSet();
            return set != null && set.contains(LIB_NAME);
        } catch (Exception e) { return false; }
    }

    private static void registerInNativeLoader() {
        try {
            Set<String> set = getNativeLoaderSet();
            if (set != null) set.add(LIB_NAME);
        } catch (Exception e) {
            LOGGER.warn("[webrtc] Could not register in NativeLoader: {}", e.getMessage());
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
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        return "linux";
    }

    private static String getArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.equals("amd64") || arch.equals("x86_64")) return "x86_64";
        if (arch.equals("aarch64") || arch.equals("arm64")) return "aarch64";
        return "x86_64";
    }

    private static void downloadFile(String urlStr, Path target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", "msf-friends-mod/1.0");
        int status = conn.getResponseCode();
        if (status != 200) { conn.disconnect(); throw new IOException("HTTP " + status); }
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { conn.disconnect(); }
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
