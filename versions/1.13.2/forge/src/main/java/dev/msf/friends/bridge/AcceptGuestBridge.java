package dev.msf.friends.bridge;

import dev.msf.friends.webrtc.RtcChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.ReadTimeoutHandler;
import dev.msf.friends.util.Logging;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Server-side bridge for MC 1.13.2 Forge: accepts incoming P2P guest connections.
 *
 * <p>1.13.2 differences from 1.18.2:
 * <ul>
 *   <li>{@code Connection} → {@code NetworkManager}</li>
 *   <li>{@code PacketFlow} → {@code EnumPacketDirection} / {@code NetworkDirection}</li>
 *   <li>{@code ServerConnectionListener} → {@code NetworkSystem} / server connection list</li>
 *   <li>{@code ServerHandshakePacketListenerImpl} → {@code ServerLoginNetHandler} / {@code NetHandlerHandshakeServer}</li>
 *   <li>Inline pipeline setup instead of {@code configureSerialization}</li>
 *   <li>Uses {@link ForgeReflect} for SRG fallback in production</li>
 *   <li>Uses log4j Logger</li>
 * </ul>
 */
public final class AcceptGuestBridge {
    private static final Logger LOGGER = Logging.get();
    private static final DefaultEventLoopGroup EVENT_LOOP_GROUP = new DefaultEventLoopGroup(1, r -> {
        Thread t = new Thread(r, "msf-rtc-server-netty");
        t.setDaemon(true);
        return t;
    });

    private AcceptGuestBridge() {}

    /**
     * Called on the integrated server thread. Creates a NetworkManager for the guest,
     * configures the pipeline, and hands it to the server.
     */
    public static void acceptGuest(Channel rtcChannel, UUID guestProfileId) throws Exception {
        LOGGER.info("[accept-guest] accepting guest profileId={}", guestProfileId);

        Class<?> minecraftClass = ForgeReflect.mcClass("net.minecraft.client.Minecraft");
        Class<?> networkManagerClass = ForgeReflect.mcClass("net.minecraft.network.NetworkManager");
        Class<?> minecraftServerClass = ForgeReflect.mcClass("net.minecraft.server.MinecraftServer");
        Class<?> integratedServerClass = ForgeReflect.mcClass("net.minecraft.server.integrated.IntegratedServer");

        Object minecraft = ForgeReflect.mcMethod(minecraftClass, "getInstance").invoke(null);
        Object server = ForgeReflect.mcMethod(minecraftClass, "getIntegratedServer").invoke(minecraft);
        if (server == null) {
            LOGGER.warn("[accept-guest] no integrated server running");
            rtcChannel.close();
            return;
        }

        // Set profile attribute on the channel for the login mixin to pick up
        rtcChannel.attr(RtcChannel.INTENDED_PROFILE_ID).set(guestProfileId);

        // Resolve server connection list
        final List<Object> connections = resolveServerConnections(server, minecraftServerClass);
        if (connections == null) {
            LOGGER.warn("[accept-guest] could not resolve server connections list");
            rtcChannel.close();
            return;
        }

        // Resolve handshake listener
        Class<?> handshakeListenerClass = resolveClass(
                "net.minecraft.network.NetHandlerLoginServer",
                "net.minecraft.network.login.ServerLoginNetHandler",
                "net.minecraft.server.network.ServerHandshakePacketListenerImpl");
        Class<?> packetListenerClass = resolveClass(
                "net.minecraft.network.INetHandler",
                "net.minecraft.network.PacketListener");

        final Constructor<?> handshakeCtor = handshakeListenerClass.getConstructors()[0];
        final Method setListener = resolveMethod(networkManagerClass,
                "setNetHandler", "setListener", packetListenerClass);

        final Object fServer = server;

        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                // Create unbound NetworkManager for server side
                Object connection = ConnectionFactory.createUnbound(
                        ConnectionFactory.PacketFlowDirection.SERVERBOUND);

                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));

                // Set up inline pipeline for server-side (decodes SERVERBOUND, encodes CLIENTBOUND)
                ConnectionBridge.setupInlinePipeline(pipeline, false);

                // Set handshake listener on the connection
                try {
                    Object handshakeListener;
                    // Try (MinecraftServer, NetworkManager) first, then (IntegratedServer, NetworkManager)
                    try {
                        handshakeListener = handshakeCtor.newInstance(fServer, connection);
                    } catch (Exception e) {
                        // Some versions take different args
                        handshakeListener = handshakeCtor.newInstance(connection);
                    }
                    setListener.invoke(connection, handshakeListener);
                } catch (Exception e) {
                    LOGGER.warn("[accept-guest] failed to set handshake listener", e);
                }

                // Add to server connections list
                synchronized (connections) {
                    connections.add(connection);
                }

                LOGGER.info("[accept-guest] guest connection accepted, profileId={}", guestProfileId);
            }
        });

        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
    }

    /**
     * Resolves the server's active connection list.
     * 1.13.2 uses IntegratedServer → MinecraftServer.connection (ServerConnectionListener / NetworkSystem)
     * → getConnections() / getNetworkManagers().
     */
    @SuppressWarnings("unchecked")
    private static List<Object> resolveServerConnections(Object server, Class<?> minecraftServerClass) {
        // Try MinecraftServer.connection field (ServerConnectionListener)
        try {
            Field connectionField = ForgeReflect.mcField(minecraftServerClass, "connection");
            connectionField.setAccessible(true);
            Object scl = connectionField.get(server);
            if (scl != null) {
                try {
                    Method getConnections = scl.getClass().getMethod("getConnections");
                    return (List<Object>) getConnections.invoke(scl);
                } catch (NoSuchMethodException ignored) {}
                // Try getNetworkManagers
                try {
                    Method getNetworkManagers = scl.getClass().getMethod("getNetworkManagers");
                    return (List<Object>) getNetworkManagers.invoke(scl);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.debug("[accept-guest] connection field lookup failed: {}", e.getMessage());
        }

        // Try networkIo field (common name in some versions)
        try {
            Field networkIoField = ForgeReflect.mcField(minecraftServerClass, "networkIo");
            networkIoField.setAccessible(true);
            Object scl = networkIoField.get(server);
            if (scl != null) {
                Method getConnections = scl.getClass().getMethod("getConnections");
                return (List<Object>) getConnections.invoke(scl);
            }
        } catch (Exception e) {
            LOGGER.debug("[accept-guest] networkIo field lookup failed: {}", e.getMessage());
        }

        // Try calling getNetworkSystem() method on the server
        try {
            Method getNetworkSystem = server.getClass().getMethod("getNetworkSystem");
            Object ns = getNetworkSystem.invoke(server);
            if (ns != null) {
                try {
                    Method getConnections = ns.getClass().getMethod("getConnections");
                    return (List<Object>) getConnections.invoke(ns);
                } catch (NoSuchMethodException ignored) {}
                try {
                    Method getNetworkManagers = ns.getClass().getMethod("getNetworkManagers");
                    return (List<Object>) getNetworkManagers.invoke(ns);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.debug("[accept-guest] getNetworkSystem lookup failed: {}", e.getMessage());
        }

        return null;
    }

    private static Method resolveMethod(Class<?> owner, String primary, String fallback, Class<?>... paramTypes) throws NoSuchMethodException {
        try {
            return ForgeReflect.mcMethod(owner, primary, paramTypes);
        } catch (NoSuchMethodException e) {
            return ForgeReflect.mcMethod(owner, fallback, paramTypes);
        }
    }

    private static Class<?> resolveClass(String... candidates) throws ClassNotFoundException {
        for (String candidate : candidates) {
            try {
                return ForgeReflect.mcClass(candidate);
            } catch (ClassNotFoundException ignored) {}
        }
        throw new ClassNotFoundException("None of the class candidates found: " + String.join(", ", candidates));
    }
}
