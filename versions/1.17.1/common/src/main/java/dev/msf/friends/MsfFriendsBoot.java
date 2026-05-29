package dev.msf.friends;

import dev.msf.friends.bridge.HeadlessMinecraftBridge;
import dev.msf.friends.bridge.MinecraftBridge;
import com.mojang.authlib.yggdrasil.YggdrasilFriendsService;
import dev.msf.friends.p2p.P2PManager;
import dev.msf.friends.p2p.client.SignalingServiceClient;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.RemoteFriendListUpdateHandler;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.PclDetector;
import dev.msf.friends.util.TurnPrefs;
import dev.msf.friends.util.WebRtcNativeLoader;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.net.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MsfFriendsBoot {
    private static final Logger LOGGER = Logging.get();
    private static MsfFriendsBoot INSTANCE;
    private final ScheduledExecutorService tick = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "msf-tick"); t.setDaemon(true); return t;
    });
    private volatile MinecraftBridge bridge;
    private volatile PlayerSocialManager social;
    private volatile P2PManager p2p;
    public static MsfFriendsBoot get() { return INSTANCE; }
    public MinecraftBridge bridge() { return bridge; }
    public PlayerSocialManager social() { return social; }
    public P2PManager p2p() { return p2p; }
    public static void start(Path webrtcCacheDir, Path configDir) {
        INSTANCE = new MsfFriendsBoot();
        NotificationPrefs.init(configDir);
        TurnPrefs.init(configDir);
        applyDevOverridesIfPresent();
        LOGGER.info("[boot] msf-friends loading (1.17.1)");
        Thread pclThread = new Thread(PclDetector::checkAndOpenIfPCL, "msf-pcl-detect");
        pclThread.setDaemon(true);
        pclThread.start();
        Thread t = new Thread(() -> INSTANCE.boot(webrtcCacheDir), "msf-friends-boot");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::shutdown, "msf-friends-shutdown"));
    }
    private static void applyDevOverridesIfPresent() {
        String overrideUser = System.getProperty("msf.dev.user");
        String overrideToken = System.getProperty("msf.dev.token");
        String overrideUuid = System.getProperty("msf.dev.uuid");
        if (overrideUser != null && overrideToken != null && overrideUuid != null) {
            HeadlessMinecraftBridge.override = new HeadlessMinecraftBridge.CredentialOverride(
                    UUID.fromString(overrideUuid), overrideToken, overrideUser);
            LOGGER.info("[boot] using -Dmsf.dev.* override credentials for {}", overrideUser);
        }
    }
    private static String getProdServicesHost() {
        try {
            Class<?> yggdrasilEnvClass = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilEnvironment");
            Object[] enumConstants = yggdrasilEnvClass.getEnumConstants();
            Object prod = null;
            for (Object c : enumConstants) {
                if (c.toString().contains("PROD")) { prod = c; break; }
            }
            if (prod == null && enumConstants.length > 0) prod = enumConstants[0];
            if (prod != null) {
                try {
                    Method getEnv = prod.getClass().getMethod("getEnvironment");
                    Object env = getEnv.invoke(prod);
                    Method servicesHost = env.getClass().getMethod("servicesHost");
                    return (String) servicesHost.invoke(env);
                } catch (NoSuchMethodException ignored) {}
                try {
                    Object env = null;
                    try {
                        java.lang.reflect.Field envField = prod.getClass().getDeclaredField("environment");
                        envField.setAccessible(true);
                        env = envField.get(prod);
                    } catch (NoSuchFieldException ignored2) {
                        env = prod;
                    }
                    if (env != null) {
                        try {
                            java.lang.reflect.Field shField = env.getClass().getDeclaredField("servicesHost");
                            shField.setAccessible(true);
                            return (String) shField.get(env);
                        } catch (NoSuchFieldException ignored3) {}
                        try {
                            Method sh = env.getClass().getMethod("servicesHost");
                            return (String) sh.invoke(env);
                        } catch (NoSuchMethodException ignored4) {}
                    }
                } catch (Exception ignored) {}
            }
        } catch (ClassNotFoundException e) {
            // Expected for some versions
        } catch (Exception e) {
            LOGGER.debug("[boot] YggdrasilEnvironment.PROD reflection failed", e);
        }
        LOGGER.info("[boot] Using hard-coded PROD servicesHost");
        return "https://api.minecraftservices.com";
    }
    private void boot(Path webrtcCacheDir) {
        try {
            this.bridge = new HeadlessMinecraftBridge();
            HeadlessMinecraftBridge.Snapshot creds = waitForCredentials(120_000);
            if (creds == null) {
                LOGGER.warn("[boot] No Minecraft user available after 120s; mod will not start.");
                return;
            }
            String servicesHost = getProdServicesHost();
            LOGGER.info("[boot] servicesHost: {}", servicesHost);
            YggdrasilFriendsService friendsService;
            try {
                friendsService = new YggdrasilFriendsService(
                        creds.accessToken(), Proxy.NO_PROXY,
                        servicesHost + "/friends",
                        servicesHost + "/player/attributes",
                        servicesHost + "/presence");
            } catch (Throwable e) {
                LOGGER.warn("[boot] Failed to create YggdrasilFriendsService; friends system disabled", e);
                return;
            }
            RemoteFriendListUpdateHandler updater = new RemoteFriendListUpdateHandler(friendsService, bridge);
            PlayerSocialManager socialMgr = new PlayerSocialManager(bridge, friendsService, updater);
            socialMgr.startOnlineMode();
            this.social = socialMgr;
            socialMgr.getRemoteFriendListUpdateHandler().setForegroundDecider(() -> true);
            SignalingServiceClient.UserCredentials cred = SignalingServiceClient.staticCredentials(
                    creds.accessToken(), creds.profileId(), creds.userName());
            WebRtcNativeLoader.ensureLoaded(webrtcCacheDir);
            P2PManager p2pMgr = new P2PManager(bridge, cred, () -> socialMgr);
            this.p2p = p2pMgr;
            tick.scheduleAtFixedRate(() -> {
                try { socialMgr.getPresenceHandler().tick(); }
                catch (Throwable t) { LOGGER.warn("[tick] threw", t); }
            }, 1L, 1L, TimeUnit.SECONDS);
            LOGGER.info("[boot] msf-friends ready (user={}, signaling env={})",
                    creds.userName(), p2pMgr.signaling().environment());
        } catch (Throwable t) {
            LOGGER.error("[boot] msf-friends failed to start", t);
        }
    }
    private HeadlessMinecraftBridge.Snapshot waitForCredentials(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var snap = HeadlessMinecraftBridge.tryFetch();
            if (snap.isPresent()) return snap.get();
            try { Thread.sleep(500); } catch (InterruptedException e) { return null; }
        }
        return null;
    }
    private void shutdown() {
        try { tick.shutdownNow(); } catch (Throwable ignore) {}
        try { if (p2p != null) p2p.shutdown(); } catch (Throwable ignore) {}
        try { if (social != null) {
            social.stopOnlineMode();
            social.getRemoteFriendListUpdateHandler().close();
        } } catch (Throwable ignore) {}
    }
}
