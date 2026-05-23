package dev.msf.friends;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilEnvironment;
import dev.msf.friends.bridge.HeadlessMinecraftBridge;
import dev.msf.friends.bridge.MinecraftBridge;
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
    public MinecraftBridge bridge()     { return bridge; }
    public PlayerSocialManager social() { return social; }
    public P2PManager p2p()             { return p2p; }

    public static void start(Path webrtcCacheDir, Path configDir) {
        INSTANCE = new MsfFriendsBoot();
        NotificationPrefs.init(configDir);
        TurnPrefs.init(configDir);
        applyDevOverridesIfPresent();
        LOGGER.info("[boot] msf-friends loading");
        Thread pclThread = new Thread(PclDetector::checkAndOpenIfPCL, "msf-pcl-detect");
        pclThread.setDaemon(true);
        pclThread.start();
        Thread t = new Thread(() -> INSTANCE.boot(webrtcCacheDir), "msf-friends-boot");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::shutdown, "msf-friends-shutdown"));
    }

    private static void applyDevOverridesIfPresent() {
        String overrideUser  = System.getProperty("msf.dev.user");
        String overrideToken = System.getProperty("msf.dev.token");
        String overrideUuid  = System.getProperty("msf.dev.uuid");
        if (overrideUser != null && overrideToken != null && overrideUuid != null) {
            HeadlessMinecraftBridge.override = new HeadlessMinecraftBridge.CredentialOverride(
                    UUID.fromString(overrideUuid), overrideToken, overrideUser);
            LOGGER.info("[boot] using -Dmsf.dev.* override credentials for {}", overrideUser);
        }
    }

    private void boot(Path webrtcCacheDir) {
        try {
            this.bridge = new HeadlessMinecraftBridge();
            HeadlessMinecraftBridge.Snapshot creds = waitForCredentials(120_000);
            if (creds == null) {
                LOGGER.warn("[boot] No Minecraft user available after 120s; mod will not start. "
                        + "Set -Dmsf.dev.user/-Dmsf.dev.token/-Dmsf.dev.uuid to override.");
                return;
            }

            YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(
                    Proxy.NO_PROXY, YggdrasilEnvironment.PROD.getEnvironment());

            FriendsService friendsService;
            try {
                friendsService = createFriendsService(auth, creds.accessToken());
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("[boot] authlib does not have createFriendsService() (likely 7.0.63); "
                        + "falling back to bundled YggdrasilFriendsService");
                friendsService = new com.mojang.authlib.yggdrasil.YggdrasilFriendsService(
                        creds.accessToken(), Proxy.NO_PROXY, YggdrasilEnvironment.PROD.getEnvironment());
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
                try {
                    socialMgr.getPresenceHandler().tick();
                } catch (Throwable t) {
                    LOGGER.warn("[tick] threw", t);
                }
            }, 1L, 1L, TimeUnit.SECONDS);

            LOGGER.info("[boot] msf-friends ready (user={}, signaling env={})",
                    creds.userName(), p2pMgr.signaling().environment());
        } catch (Throwable t) {
            LOGGER.error("[boot] msf-friends failed to start", t);
        }
    }

    private static FriendsService createFriendsService(YggdrasilAuthenticationService auth, String accessToken)
            throws ReflectiveOperationException {
        Object result = auth.getClass().getMethod("createFriendsService", String.class).invoke(auth, accessToken);
        if (result instanceof FriendsService friendsService) {
            return friendsService;
        }
        throw new ReflectiveOperationException("createFriendsService() returned unexpected type: "
                + (result == null ? "null" : result.getClass().getName()));
    }

    private static String detectAuthlibVersion() {
        try {
            Package pkg = YggdrasilAuthenticationService.class.getPackage();
            String v = pkg != null ? pkg.getImplementationVersion() : null;
            if (v != null) return v;
            var cs = YggdrasilAuthenticationService.class.getProtectionDomain().getCodeSource();
            if (cs != null) return cs.getLocation().toString();
        } catch (Throwable ignore) {}
        return "(unknown)";
    }

    private HeadlessMinecraftBridge.Snapshot waitForCredentials(long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            var maybe = HeadlessMinecraftBridge.tryFetch();
            if (maybe.isPresent()) return maybe.get();
            Thread.sleep(500);
        }
        return null;
    }

    public void shutdown() {
        try { tick.shutdownNow(); } catch (Throwable ignore) {}
        try { if (p2p != null) p2p.shutdown(); }   catch (Throwable ignore) {}
        try { if (social != null) {
            social.stopOnlineMode();
            social.getRemoteFriendListUpdateHandler().close();
        } } catch (Throwable ignore) {}
    }
}
