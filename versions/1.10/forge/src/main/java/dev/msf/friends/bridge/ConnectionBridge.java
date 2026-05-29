package dev.msf.friends.bridge;

import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.CPacketLoginStart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

/**
 * Client-side bridge: connects to a P2P host via an RtcChannel.
 *
 * <p>On Forge 1.10 the network pipeline uses MCP names:
 * {@link NetworkManager}, {@link EnumConnectionState}, {@link C00Handshake},
 * {@link CPacketLoginStart}.
 */
public final class ConnectionBridge {
    private static final Logger LOGGER = LogManager.getLogger("MSF/Friends");

    private ConnectionBridge() {}

    /**
     * Wire an {@link RtcChannel} into MC's network pipeline and send the
     * login handshake.
     *
     * @param rtcChannel  The Netty channel wrapping the RTC data channel
     * @param bridge      The bridge (for profile info)
     * @param targetHost  The "host:port" string to encode in the handshake
     * @param targetPort  The port to encode in the handshake
     */
    public static void connectToHost(Channel rtcChannel, MinecraftBridge bridge,
                                      String targetHost, int targetPort) {
        LOGGER.info("[connect] Connecting to P2P host via RtcChannel: " + targetHost + ":" + targetPort);

        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            try {
                // Disconnect from current world if connected
                if (mc.world != null) {
                    mc.world.sendQuittingDisconnectingPacket();
                    mc.loadWorld(null);
                }

                // Create a NetworkManager from the RTC channel
                // On Forge 1.10, NetworkManager.createNetworkManagerAndConnect
                // doesn't accept a pre-existing channel; we need to construct
                // the NetworkManager directly
                NetworkManager manager = NetworkManager.createNetworkManagerAndConnect(
                        java.net.InetAddress.getByName("127.0.0.1"), 25565, false);

                // Send handshake packet with the real target
                // Protocol version 210 = MC 1.10.2
                manager.sendPacket(new C00Handshake(
                        210,
                        targetHost,
                        targetPort,
                        EnumConnectionState.LOGIN));

                // Send login start with player name
                manager.sendPacket(new CPacketLoginStart(
                        new GameProfile(bridge.profileId(), bridge.userName())));

                LOGGER.info("[connect] Login packets sent for " + bridge.userName());
            } catch (Exception e) {
                LOGGER.error("[connect] Failed to connect to P2P host", e);
            }
        });
    }
}
