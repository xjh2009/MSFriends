package dev.msf.friends;

import dev.msf.friends.bridge.HeadlessMinecraftBridge1112;
import dev.msf.friends.bridge.MinecraftBridge1112;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Boot logic for MC 1.11.2 Forge - fully self-contained (Java 8).
 *
 * IMPORTANT: No lambdas or method references!
 * Java 8 javac has a known bug that corrupts the constant pool.
 */
public final class MsfFriendsBoot1112 {

    private static final Logger LOGGER = Logging1112.get();
    private static MsfFriendsBoot1112 INSTANCE;

    private ScheduledExecutorService tick;
    private volatile MinecraftBridge1112 bridge;
    private volatile boolean ready = false;
    private List<FriendsScreen1112.FriendEntry> friendEntries;

    public static MsfFriendsBoot1112 get() { return INSTANCE; }
    public MinecraftBridge1112 bridge() { return bridge; }
    public boolean isReady() { return ready; }
    public List<FriendsScreen1112.FriendEntry> getFriendEntries() { return friendEntries; }

    public static void start(Path webrtcCacheDir, Path configDir) {
        INSTANCE = new MsfFriendsBoot1112();
        INSTANCE.friendEntries = Collections.synchronizedList(new ArrayList<FriendsScreen1112.FriendEntry>());
        INSTANCE.tick = Executors.newSingleThreadScheduledExecutor(new MsfThreadFactory());
        LOGGER.info("[boot] MSF Friends 1.11.2 loading (config=" + configDir + ")");
        applyDevOverrides();
        Thread bt = new Thread(new MsfBootRunnable(webrtcCacheDir), "msf-friends-boot-1112");
        bt.setDaemon(true);
        bt.start();
        Runtime.getRuntime().addShutdownHook(new Thread(new MsfShutdownRunnable(), "msf-friends-shutdown-1112"));
    }

    private static void applyDevOverrides() {
        String u = System.getProperty("msf.dev.user");
        String t = System.getProperty("msf.dev.token");
        String id = System.getProperty("msf.dev.uuid");
        if (u != null && t != null && id != null) {
            HeadlessMinecraftBridge1112.override = new HeadlessMinecraftBridge1112.CredentialOverride(
                    UUID.fromString(id), t, u);
            LOGGER.info("[boot] using -Dmsf.dev.* override credentials for " + u);
        }
    }

    private void boot(Path cacheDir) {
        try {
            this.bridge = new HeadlessMinecraftBridge1112();
            LOGGER.info("[boot] waiting for Minecraft session...");
            HeadlessMinecraftBridge1112.Snapshot creds = waitForCreds(120000);
            if (creds == null) {
                LOGGER.warn("[boot] No session after 120s; aborting.");
                return;
            }
            LOGGER.info("[boot] Authenticated as " + creds.userName + " (" + creds.profileId + ")");
            friendEntries.add(new FriendsScreen1112.FriendEntry(UUID.randomUUID(), "ExampleFriend_1", true));
            friendEntries.add(new FriendsScreen1112.FriendEntry(UUID.randomUUID(), "ExampleFriend_2", false));
            this.ready = true;
            tick.scheduleAtFixedRate(new MsfTickRunnable(), 5L, 5L, TimeUnit.SECONDS);
            LOGGER.info("[boot] MSF Friends 1.11.2 ready (user=" + creds.userName + ")");
        } catch (Throwable ex) {
            LOGGER.error("[boot] MSF Friends 1.11.2 failed", ex);
        }
    }

    private HeadlessMinecraftBridge1112.Snapshot waitForCreds(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            HeadlessMinecraftBridge1112.Snapshot s = HeadlessMinecraftBridge1112.tryFetchOrNull();
            if (s != null) return s;
            try { Thread.sleep(500); } catch (InterruptedException e) { return null; }
        }
        return null;
    }

    private void doShutdown() {
        try { tick.shutdownNow(); } catch (Throwable ignored) {}
        LOGGER.info("[boot] MSF Friends 1.11.2 shut down");
    }

    /* ---- inner classes (no lambdas!) ---- */

    static final class MsfThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "msf-tick-1112");
            t.setDaemon(true);
            return t;
        }
    }

    static final class MsfBootRunnable implements Runnable {
        final Path cacheDir;
        MsfBootRunnable(Path cd) { cacheDir = cd; }
        public void run() { if (INSTANCE != null) INSTANCE.boot(cacheDir); }
    }

    static final class MsfShutdownRunnable implements Runnable {
        public void run() { if (INSTANCE != null) INSTANCE.doShutdown(); }
    }

    static final class MsfTickRunnable implements Runnable {
        public void run() {
            try { LOGGER.debug("[tick] presence tick"); }
            catch (Throwable t) { LOGGER.warn("[tick] threw", t); }
        }
    }
}
