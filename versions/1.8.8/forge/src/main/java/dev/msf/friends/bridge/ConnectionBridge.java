package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.client.C00PacketLoginStart;
import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Client-side bridge for MC 1.8.8: wires an RtcChannel into MC's NetworkManager
 * system and initiates the login handshake to a remote P2P host.
 *
 * Uses reflection instead of Mixin for field access.
 */
public final class ConnectionBridge {
    private static final Logger LOGGER = Logging.get();
    private static final NioEventLoopGroup EVENT_LOOP_GROUP = new NioEventLoopGroup(1, r -> {
        Thread t = new Thread(r, "msf-rtc-netty");
        t.setDaemon(true);
        return t;
    });

    /**
     * Bridge into Minecraft's client connection system by wrapping an RtcChannel
     * into an MC NetworkManager and initiating a login handshake to a P2P host.
     */
    public static void joinHost(Channel rtcChannel, String playerName, UUID profileId) throws Exception {
        LOGGER.info("[connection-bridge] joinHost starting for player={} uuid={}", playerName, profileId);

        final Minecraft mc = Minecraft.getMinecraft();

        // Disconnect current world if any
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
        }

        // Prepare ServerData
        final ServerData serverInfo = new ServerData("Online", "rtc-peer", false);

        // ChannelInitializer: configure pipeline
        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                // Create NetworkManager (clientbound)
                NetworkManager connection = ConnectionFactory.createUnbound(EnumPacketDirection.CLIENTBOUND);

                // Build pipeline
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                pipeline.addLast("packet_handler", connection);

                // Set channel/address via reflection
                NetworkManagerAccessor.setChannel(connection, ch);
                NetworkManagerAccessor.setAddress(connection,
                        ch.remoteAddress() != null ? ch.remoteAddress() : new InetSocketAddress("rtc-peer", 25565));

                // Configure LOGIN state
                connection.setConnectionState(EnumConnectionState.LOGIN);

                // Create login handler (3rd arg: previous screen, null since we connect via P2P)
                NetHandlerLoginClient loginHandler = new NetHandlerLoginClient(
                        connection, mc, null);
                connection.setNetHandler(loginHandler);

                // Set current server entry
                mc.setServerData(new ServerData("Online", "rtc-peer", false));

                // Send hello packet
                GameProfile profile = new GameProfile(profileId, playerName);
                connection.sendPacket(new C00PacketLoginStart(profile));

                // Set pending connection via reflection
                try {
                    Field pendingField = Minecraft.class.getDeclaredField("theServer");
                    pendingField.setAccessible(true);
                    pendingField.set(mc, connection);
                } catch (Exception e) {
                    LOGGER.warn("[connection-bridge] Could not set pending connection field", e);
                }

                LOGGER.info("[connection-bridge] initChannel complete, pipeline ready");
            }
        });

        // Register: triggers initChannel
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();

        LOGGER.info("[connection-bridge] joinHost complete");
    }

    private ConnectionBridge() {}
}
