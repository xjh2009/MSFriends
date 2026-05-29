package dev.msf.friends;

import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.P2PManager;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.Logging;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class MsfFriendsClient implements ClientModInitializer {
    private static final Logger LOGGER = Logging.get();

    private static MsfFriendsClient INSTANCE;

    public static MsfFriendsClient get() { return INSTANCE; }
    public MinecraftBridge bridge()     { var b = MsfFriendsBoot.get(); return b != null ? b.bridge() : null; }
    public PlayerSocialManager social() { var b = MsfFriendsBoot.get(); return b != null ? b.social() : null; }
    public P2PManager p2p()             { var b = MsfFriendsBoot.get(); return b != null ? b.p2p() : null; }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        var gameDir = FabricLoader.getInstance().getGameDir();
        var configDir = FabricLoader.getInstance().getConfigDir();
        var cacheDir = gameDir.resolve("libraries/dev/onvoid/webrtc/webrtc-java/0.14.0");
        LOGGER.info("[boot] Fabric entry point, starting boot (1.17.1)");
        MsfFriendsBoot.start(cacheDir, configDir);
    }
}
