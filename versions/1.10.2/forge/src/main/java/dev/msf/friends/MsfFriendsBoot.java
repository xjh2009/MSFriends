package dev.msf.friends;

import dev.msf.friends.authlib.FriendsService;
import dev.msf.friends.authlib.YggdrasilFriendsService;
import dev.msf.friends.bridge.HeadlessMinecraftBridge;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.P2PManager;
import dev.msf.friends.p2p.client.SignalingServiceClient;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.RemoteFriendListUpdateHandler;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bootstrap logic for MSF Friends on 1.10.2 Forge.
 * 
 * Full version: WebRTC/P2P friend list, presence tracking, signaling.
 */
public final class MsfFriendsBoot {

    private static final Logger LOGGER = Logging.get();
    private static MsfFriendsBoot INSTANCE;

    private final ScheduledExecutorService tick = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "msf-tick");
        t.setDaemon(true);
        return t;
    });

    private volatile MinecraftBridge bridge;
    private volatile PlayerSocialManager socialManager;
    private volatile P2PManager p2pManager;
    private volatile FriendsService friendsService;
    private volatile boolean started = false;

    public static MsfFriendsBoot get() {
        return INSTANCE;
    }

    public MinecraftBridge bridge() {
        return bridge;
    }

    public PlayerSocialManager socialManager() {
        return socialManager;
    }

    public P2PManager p2pManager() {
        return p2pManager;
    }

    public FriendsService friendsService() {
        return friendsService;
    }

    public static void start(Path cacheDir, Path configDir) {
        INSTANCE = new MsfFriendsBoot();
        LOGGER.info("[boot] msf-friends 1.10.2 loading");

        // Initialize prefs
        TurnPrefs.init(configDir);
        NotificationPrefs.init(configDir);

        Thread t = new Thread(new Runnable() {
            @Override public void run() { INSTANCE.boot(cacheDir, configDir); }
        }, "msf-friends-boot");
        t.setDaemon(true);
        t.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override public void run() { INSTANCE.shutdown(); }
        }, "msf-friends-shutdown"));
    }

    private void boot(Path cacheDir, Path configDir) {
        try {
            // Wait for the player to be available
            final HeadlessMinecraftBridge bridgeImpl = waitForBridge(120_000);
            if (bridgeImpl == null) {
                LOGGER.warn("[boot] No Minecraft session available after 120s; mod will not start.");
                return;
            }
            this.bridge = bridgeImpl;

            // Create the FriendsService (backport)
            this.friendsService = new YggdrasilFriendsService(bridge);

            // Create RemoteFriendListUpdateHandler
            final RemoteFriendListUpdateHandler friendListHandler = new RemoteFriendListUpdateHandler(friendsService, bridge);

            // Create PlayerSocialManager
            this.socialManager = new PlayerSocialManager(bridge, friendsService, friendListHandler);

            // Create P2PManager
            final SignalingServiceClient.UserCredentials creds = new SignalingServiceClient.UserCredentials() {
                @Override public UUID profileId() { return bridge.profileId(); }
                @Override public String userName() { return bridge.userName(); }
                @Override public String accessToken() { return bridge.accessToken(); }
            };
            this.p2pManager = new P2PManager(bridge, creds, new java.util.function.Supplier<PlayerSocialManager>() {
                @Override public PlayerSocialManager get() { return socialManager; }
            });

            // Start the presence tick (1 second interval)
            tick.scheduleWithFixedDelay(new Runnable() {
                @Override public void run() {
                    try {
                        if (socialManager != null && socialManager.isOnlineMode()) {
                            socialManager.getPresenceHandler().tick();
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("[tick] presence tick failed", t);
                    }
                }
            }, 0L, 1L, TimeUnit.SECONDS);

            // Start online mode
            socialManager.startOnlineMode();

            // Set foreground decider so friend list updates every 1 minute
            friendListHandler.setForegroundDecider(new java.util.function.BooleanSupplier() {
                @Override public boolean getAsBoolean() { return true; }
            });

            this.started = true;
            LOGGER.info("[boot] msf-friends 1.10.2 ready (user={})", bridge.userName());
        } catch (Throwable t) {
            LOGGER.error("[boot] msf-friends 1.10.2 failed to start", t);
        }
    }

    private HeadlessMinecraftBridge waitForBridge(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.getSession() != null && mc.getSession().getPlayerID() != null) {
                    String id = mc.getSession().getPlayerID();
                    if (id != null && !id.isEmpty()) {
                        return new HeadlessMinecraftBridge();
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return null;
            }
        }
        return null;
    }

    private void shutdown() {
        try {
            started = false;
            if (socialManager != null) {
                socialManager.stopOnlineMode();
            }
            if (p2pManager != null) {
                p2pManager.shutdown();
            }
            tick.shutdownNow();
        } catch (Throwable ignore) {
        }
    }
}
