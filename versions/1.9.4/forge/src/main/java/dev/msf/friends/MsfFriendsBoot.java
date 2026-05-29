package dev.msf.friends;

import dev.msf.friends.bridge.HeadlessMinecraftBridge;
import dev.msf.friends.event.FriendsEventHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

/**
 * Main entry point for the MSF Friends mod on Forge 1.9.4.
 * Self-contained — does not depend on :common (Java 17).
 */
@Mod(modid = MsfFriendsConstants.MOD_ID,
     name = MsfFriendsConstants.MOD_NAME,
     version = "0.1.0",
     acceptedMinecraftVersions = "[1.9.4]",
     clientSideOnly = true)
public class MsfFriendsBoot {

    private static MsfFriendsBoot instance;
    private volatile boolean ready = false;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MsfFriendsConstants.logLifecycle("preInit");
        instance = this;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MsfFriendsConstants.logLifecycle("init — booting (self-contained 1.9.4)");
        Logger LOGGER = MsfFriendsConstants.LOGGER;

        // Register Forge event handler (button injection + key binding)
        FriendsEventHandler.register();

        new Thread(() -> {
            try {
                LOGGER.info("[MSF/Friends] Waiting for credentials...");
                HeadlessMinecraftBridge.waitForCredentials(120_000L);
                LOGGER.info("[MSF/Friends] Credentials acquired — ready.");
                ready = true;

                // Full P2P / social bootstrapping will be wired here
                // once the Java 8 compatible versions of those classes are ported.
                LOGGER.info("[MSF/Friends] Mod initialised (simplified 1.9.4 build).");
            } catch (Exception e) {
                LOGGER.error("[MSF/Friends] Boot failed", e);
            }
        }, "MSF-Friends-Boot").start();
    }

    public static MsfFriendsBoot get() {
        return instance;
    }

    public boolean isReady() {
        return ready;
    }
}
