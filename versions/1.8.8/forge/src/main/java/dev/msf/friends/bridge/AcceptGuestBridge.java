package dev.msf.friends.bridge;

import dev.msf.friends.util.Logging;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetHandlerHandshakeTCP;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

/**
 * Server-side bridge for MC 1.8.8: accepts an incoming P2P guest connection.
 *
 * <p>MCP 1.8.8 names: NetworkManager, EnumPacketDirection, EnumConnectionState,
 * NetworkSystem, NetHandlerHandshakeTCP.
 */
public final class AcceptGuestBridge {
    private static final Logger LOGGER = Logging.get();
    private static final NioEventLoopGroup EVENT_LOOP_GROUP = new NioEventLoopGroup(1, r -> {
        Thread t = new Thread(r, "msf-rtc-server-netty");
        t.setDaemon(true);
        return t;
    });

    @SuppressWarnings("unchecked")
    public static void acceptGuest(Channel rtcChannel, UUID guestProfileId) throws Exception {
        LOGGER.info("[accept-guest] accepting guest profileId={}", guestProfileId);

        final Minecraft mc = Minecraft.getMinecraft();
        MinecraftServer server = mc.getIntegratedServer();
        if (server == null) {
            LOGGER.warn("[accept-guest] no singleplayer server running");
            rtcChannel.close();
            return;
        }

        // Get NetworkSystem via reflection
        NetworkSystem networkSystem = (NetworkSystem) MinecraftServerAccessor.getNetworkSystem(server);
        if (networkSystem == null) {
            LOGGER.warn("[accept-guest] NetworkSystem is null");
            rtcChannel.close();
            return;
        }

        @SuppressWarnings("unchecked")
        List<NetworkManager> connections = (List<NetworkManager>) MinecraftServerAccessor.getNetworkManagers(networkSystem);

        final MinecraftServer fServer = server;

        // Atomic pipeline setup via ChannelInitializer
        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                // Create NetworkManager (serverbound)
                NetworkManager connection = ConnectionFactory.createUnbound(EnumPacketDirection.SERVERBOUND);

                // Pipeline setup
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                pipeline.addLast("packet_handler", connection);

                // Set channel/address via reflection
                NetworkManagerAccessor.setChannel(connection, ch);
                NetworkManagerAccessor.setAddress(connection,
                        ch.remoteAddress() != null ? ch.remoteAddress() : new InetSocketAddress("rtc-guest", 25565));

                // setState configures LOGIN-aware codecs
                connection.setConnectionState(EnumConnectionState.LOGIN);

                // Set handshake listener
                NetHandlerHandshakeTCP handshakeListener =
                        new NetHandlerHandshakeTCP(fServer, connection);
                connection.setNetHandler(handshakeListener);

                // Add to server's connections list
                synchronized (connections) {
                    connections.add(connection);
                }

                LOGGER.info("[accept-guest] guest connection accepted, profileId={}", guestProfileId);
            }
        });

        // Register triggers initChannel
        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
    }

    private AcceptGuestBridge() {}
}
