package dev.msf.friends;

import dev.msf.friends.bridge.HeadlessMinecraftBridge1122;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.P2PManager;
import dev.msf.friends.p2p.client.SignalingServiceClient;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.RemoteFriendListUpdateHandler;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.net.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Boot sequence for MC 1.12.2 Forge. Java 8 compatible.
 */
public final class MsfFriendsBoot1122 {
    private static final Logger LOGGER = Logging.get();
    private static MsfFriendsBoot1122 INSTANCE;

    public static volatile Path bootDir;
    public static volatile Path configDir;

    private final ScheduledExecutorService tick = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "msf-tick");
        t.setDaemon(true);
        return t;
    });

    private volatile MinecraftBridge bridge;
    private volatile PlayerSocialManager social;
    private volatile P2PManager p2p;

    public static MsfFriendsBoot1122 get() { return INSTANCE; }
    public MinecraftBridge bridge() { return bridge; }
    public PlayerSocialManager social() { return social; }
    public P2PManager p2p() { return p2p; }

    public static void start() {
        INSTANCE = new MsfFriendsBoot1122();
        if (configDir != null) {
            NotificationPrefs.init(configDir);
            TurnPrefs.init(configDir);
        }
        LOGGER.info("[boot] msf-friends loading (1.12.2)");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                INSTANCE.boot();
            }
        }, "msf-friends-boot");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                INSTANCE.shutdown();
            }
        }, "msf-friends-shutdown"));
    }

    private static String getProdServicesHost() {
        try {
            Class<?> yggdrasilEnvClass = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilEnvironment");
            Object[] enumConstants = yggdrasilEnvClass.getEnumConstants();
            Object prod = null;
            if (enumConstants != null) {
                for (Object c : enumConstants) {
                    if (c.toString().contains("PROD")) {
                        prod = c;
                        break;
                    }
                }
                if (prod == null && enumConstants.length > 0) {
                    prod = enumConstants[0];
                }
            }
            if (prod != null) {
                try {
                    Method getEnv = prod.getClass().getMethod("getEnvironment");
                    Object env = getEnv.invoke(prod);
                    Method servicesHost = env.getClass().getMethod("servicesHost");
                    return (String) servicesHost.invoke(env);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (ClassNotFoundException e) {
            // authlib < 3.x: no YggdrasilEnvironment
        } catch (Exception e) {
            LOGGER.debug("[boot] YggdrasilEnvironment.PROD reflection failed", e);
        }
        LOGGER.info("[boot] Using hard-coded PROD servicesHost");
        return "https://api.minecraftservices.com";
    }

    private void boot() {
        try {
            this.bridge = new HeadlessMinecraftBridge1122();
            HeadlessMinecraftBridge1122.Snapshot creds = waitForCredentials(120000);
            if (creds == null) {
                LOGGER.warn("[boot] No Minecraft user available after 120s; mod will not start.");
                return;
            }
            String servicesHost = getProdServicesHost();
            LOGGER.info("[boot] servicesHost: {}", servicesHost);

            com.mojang.authlib.yggdrasil.YggdrasilFriendsService friendsService;
            try {
                friendsService = new com.mojang.authlib.yggdrasil.YggdrasilFriendsService(
                        creds.accessToken, Proxy.NO_PROXY,
                        servicesHost + "/friends",
                        servicesHost + "/player/attributes",
                        servicesHost + "/presence");
            } catch (Throwable e) {
                LOGGER.warn("[boot] Failed to create YggdrasilFriendsService; friends system disabled", e);
                return;
            }

            RemoteFriendListUpdateHandler updater = new RemoteFriendListUpdateHandler(friendsService, bridge);
            final PlayerSocialManager socialMgr = new PlayerSocialManager(bridge, friendsService, updater);
            socialMgr.startOnlineMode();
            this.social = socialMgr;

            SignalingServiceClient.UserCredentials cred = new SignalingServiceClient.UserCredentials(
                    creds.profileId, creds.accessToken, creds.accessToken);

            P2PManager p2pMgr = new P2PManager(bridge, cred, new java.util.function.Supplier<PlayerSocialManager>() {
                @Override public PlayerSocialManager get() { return socialMgr; }
            });
            this.p2p = p2pMgr;

            tick.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        socialMgr.getPresenceHandler().tick();
                    } catch (Throwable t) {
                        LOGGER.warn("[tick] threw", t);
                    }
                }
            }, 1L, 1L, TimeUnit.SECONDS);

            LOGGER.info("[boot] msf-friends ready (user={})",
                    creds.userName);
        } catch (Throwable t) {
            LOGGER.error("[boot] msf-friends failed to start", t);
        }
    }

    private HeadlessMinecraftBridge1122.Snapshot waitForCredentials(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            HeadlessMinecraftBridge1122.Snapshot snap = HeadlessMinecraftBridge1122.tryFetch();
            if (snap != null) return snap;
            try { Thread.sleep(500); } catch (InterruptedException e) { return null; }
        }
        return null;
    }

    private void shutdown() {
        try { tick.shutdownNow(); } catch (Throwable ignore) {}
        try { if (p2p != null) p2p.shutdown(); } catch (Throwable ignore) {}
        try {
            if (social != null) {
                social.stopOnlineMode();
                social.getRemoteFriendListUpdateHandler().close();
            }
        } catch (Throwable ignore) {}
    }
}
