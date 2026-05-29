package dev.msf.friends;

import com.mojang.authlib.yggdrasil.YggdrasilFriendsService;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FML 6.x @Mod entry point for Forge 1.7.10.
 */
@Mod(modid = MsfFriendsConstants.MOD_ID, name = "MSF Friends", version = "1.0.0",
     useMetadata = true)
public class MsfFriendsForge {
    private static final Logger LOGGER = Logging.get(MsfFriendsForge.class);

    private static MinecraftBridge bridge;
    private static MsfFriendsBoot boot;
    private static YggdrasilFriendsService friendsService;
    private static KeyBinding friendsKey;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[MSF] MSF Friends preInit");
        NotificationPrefs.init(event.getSuggestedConfigurationFile().getParentFile());
        TurnPrefs.init(event.getSuggestedConfigurationFile().getParentFile());

        bridge = new ForgeMinecraftBridge();
        friendsService = new YggdrasilFriendsService(
                "api.minecraftservices.com", bridge.accessToken(), bridge.profileId());
        boot = new MsfFriendsBoot(bridge, friendsService);

        friendsKey = new KeyBinding("key.msf.friends", Keyboard.KEY_H, "key.categories.msf");
        cpw.mods.fml.client.registry.ClientRegistry.registerKeyBinding(friendsKey);

        FMLCommonHandler.instance().bus().register(new TickHandler());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[MSF] MSF Friends init");
        boot.start();
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (boot != null) boot.p2pManager().onHostServerStopping();
    }

    public static MsfFriendsBoot getBoot() { return boot; }
    public static MinecraftBridge getBridge() { return bridge; }
    public static YggdrasilFriendsService getFriendsService() { return friendsService; }

    /**
     * Periodic tick handler for key bindings and presence updates.
     */
    public static class TickHandler {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return;

            if (friendsKey != null && friendsKey.isPressed()) {
                mc.displayGuiScreen(new FriendsGuiScreen());
            }
        }
    }

    /**
     * Minimal MinecraftBridge implementation for Forge 1.7.10.
     */
    private static final class ForgeMinecraftBridge implements MinecraftBridge {
        private final CopyOnWriteArrayList<Runnable> serverStoppingListeners = new CopyOnWriteArrayList<Runnable>();
        private volatile boolean connectedViaP2P;

        @Override public UUID profileId() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.getSession() == null) return UUID.randomUUID();
            try {
                return UUID.fromString(mc.getSession().getPlayerID());
            } catch (Exception e) {
                // MC 1.7.10 may not have hyphenated UUID
                String id = mc.getSession().getPlayerID();
                if (id.length() == 32) {
                    id = id.substring(0,8)+"-"+id.substring(8,12)+"-"+id.substring(12,16)+"-"+id.substring(16,20)+"-"+id.substring(20);
                }
                return UUID.fromString(id);
            }
        }

        @Override public String userName() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.getSession() != null ? mc.getSession().getUsername() : "Unknown";
        }

        @Override public String accessToken() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.getSession() != null ? mc.getSession().getToken() : "";
        }

        @Override public boolean isHostingP2P() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.getIntegratedServer() != null
                    && mc.getIntegratedServer().getPublic();
        }

        @Override public boolean inLevel() {
            Minecraft mc = Minecraft.getMinecraft();
            return mc != null && mc.theWorld != null;
        }

        @Override public PresenceSharing presenceSharing() { return PresenceSharing.ALL; }
        @Override public void setPresenceSharing(PresenceSharing sharing) {}

        @Override public MultiplayerScope multiplayerScope() {
            if (isHostingP2P()) return MultiplayerScope.LAN;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.func_147104_D() != null) return MultiplayerScope.ONLINE;
            return MultiplayerScope.OFF;
        }
        @Override public void setMultiplayerScope(MultiplayerScope scope) {}

        @Override public boolean hiddenMode() { return false; }
        @Override public void setHiddenMode(boolean hidden) {}

        @Override public boolean inGameNotificationsEnabled() { return true; }
        @Override public void setInGameNotificationsEnabled(boolean enabled) {}

        @Override public boolean friendsEnabled() { return true; }
        @Override public boolean allowFriendRequests() { return true; }

        @Override public String joinHost() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getIntegratedServer() != null && mc.getIntegratedServer().getPublic()) {
                ServerData sd = mc.func_147104_D();
                return sd != null ? sd.serverIP : null;
            }
            ServerData sd = mc.func_147104_D();
            return sd != null ? sd.serverIP : null;
        }

        @Override public void joinHost(UUID peerPmid, io.netty.channel.Channel rtcChannel) {
            LOGGER.info("[MSF-1710] joinHost peer={} (P2P channel open)", peerPmid);
            connectedViaP2P = true;
            // TODO: Wire up Netty-based P2P connection into MC's network system
        }

        @Override public void acceptGuest(UUID guestProfileId, io.netty.channel.Channel rtcChannel) {
            LOGGER.info("[MSF-1710] acceptGuest guest={} (P2P channel open)", guestProfileId);
            // TODO: Wire up Netty-based P2P connection for host side
        }

        @Override public void executeOnClientThread(Runnable task) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.func_152344_a(task);
            }
        }

        @Override public void notifyToast(String toastType, String playerName, UUID profileId) {
            LOGGER.info("[MSF] Toast: {} {} {}", toastType, playerName, profileId);
            // MC 1.7.10 has no toast system; log only
        }
    }

    /**
     * Minimal friends GUI screen for MC 1.7.10.
     */
    public static class FriendsGuiScreen extends GuiScreen {
        @Override
        public void initGui() {
            super.initGui();
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();
            this.drawCenteredString(this.fontRendererObj, "MSF Friends", this.width / 2, 20, 0xFFFFFF);
            if (boot != null && boot.socialManager() != null) {
                this.drawCenteredString(this.fontRendererObj, "Friends system loaded", this.width / 2, 40, 0xAAAAAA);
            } else {
                this.drawCenteredString(this.fontRendererObj, "Friends system not initialized", this.width / 2, 40, 0xFF5555);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
