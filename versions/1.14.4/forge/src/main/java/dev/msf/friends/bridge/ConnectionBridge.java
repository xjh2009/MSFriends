package dev.msf.friends.bridge;

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
import java.util.Optional;
import java.util.UUID;

/**
 * Client-side bridge for MC 1.14.4 Forge: wires an RtcChannel into MC's
 * {@code NetworkManager} and initiates the login handshake to a remote P2P host.
 *
 * <p>1.13.2 differences from 1.18.2:
 * <ul>
 *   <li>{@code Connection} → {@code NetworkManager}</li>
 *   <li>{@code PacketFlow} → {@code EnumPacketDirection} / {@code NetworkDirection}</li>
 *   <li>{@code ServerboundHelloPacket} → {@code CLoginHelloPacket}</li>
 *   <li>{@code ClientHandshakePacketListenerImpl} → {@code ClientHandshakePacketListener}</li>
 *   <li>Inline pipeline setup instead of {@code configureSerialization}</li>
 *   <li>Uses {@link ForgeReflect} for SRG fallback in production</li>
 *   <li>Uses log4j Logger</li>
 * </ul>
 */
public final class ConnectionBridge {
    private static final Logger LOGGER = Logging.get();
    private static final DefaultEventLoopGroup EVENT_LOOP_GROUP = new DefaultEventLoopGroup(1, r -> {
        Thread t = new Thread(r, "msf-rtc-client-netty");
        t.setDaemon(true);
        return t;
    });

    private ConnectionBridge() {}

    /**
     * Called on the main client thread when the P2P connection is ready.
     *
     * @param rtcChannel the Netty channel backed by WebRTC
     * @param playerName display name of the remote host
     * @param profileId  profile id of the remote host
     */
    public static void joinHost(Channel rtcChannel, String playerName, UUID profileId) throws Exception {
        LOGGER.info("[connection-bridge] joinHost starting for player={} uuid={}", playerName, profileId);

        Class<?> networkManagerClass = ForgeReflect.mcClass("net.minecraft.network.NetworkManager");
        Class<?> minecraftClass = ForgeReflect.mcClass("net.minecraft.client.Minecraft");
        Class<?> clientHandshakeClass = ForgeReflect.mcClass("net.minecraft.client.network.ClientLoginNetHandler");
        Class<?> helloPacketClass = ForgeReflect.mcClass("net.minecraft.network.login.client.CLoginStartPacket");
        Class<?> packetClass = ForgeReflect.mcClass("net.minecraft.network.Packet");

        Object minecraft = ForgeReflect.mcMethod(minecraftClass, "getInstance").invoke(null);

        // Disconnect from current world if in one
        try {
            Field worldField = ForgeReflect.mcField(minecraftClass, "world");
            worldField.setAccessible(true);
            if (worldField.get(minecraft) != null) {
                // 1.13.2: use loadWorld(null) to disconnect
                try {
                    Class<?> clientWorldClass = Class.forName("net.minecraft.client.world.ClientWorld");
                    minecraftClass.getMethod("loadWorld", clientWorldClass).invoke(minecraft, (Object) null);
                } catch (Exception e2) {
                    LOGGER.debug("[connection-bridge] disconnect via loadWorld(null) failed: {}", e2.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[connection-bridge] no world field: {}", e.getMessage());
        }

        // Resolve the send method
        final Method sendPacket = ForgeReflect.mcMethod(networkManagerClass, "sendPacket", packetClass);

        // Resolve hello packet constructor: try GameProfile, then (String, Optional<UUID>), then (String, UUID), then (String)
        final Constructor<?> helloCtor = resolveHelloCtor(helloPacketClass);

        // Resolve handshake listener constructor
        final Constructor<?> handshakeCtor = clientHandshakeClass.getConstructors()[0];

        // Resolve setNetHandler / setListener method
        final Method setListener = resolveMethod(networkManagerClass,
                "setNetHandler", "setListener",
                ForgeReflect.mcClass("net.minecraft.network.INetHandler"));

        final Object fMinecraft = minecraft;

        rtcChannel.pipeline().addLast(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                // Create unbound NetworkManager
                Object connection = ConnectionFactory.createUnbound(
                        ConnectionFactory.PacketFlowDirection.CLIENTBOUND);

                // Set up inline pipeline for 1.13.2 (packet framing + codec)
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                setupInlinePipeline(pipeline, true);

                // Set handshake listener
                try {
                    Object handshakeListener = handshakeCtor.newInstance(connection, fMinecraft);
                    setListener.invoke(connection, handshakeListener);
                } catch (Exception e) {
                    LOGGER.warn("[connection-bridge] failed to set listener", e);
                }

                // Send hello/login packet
                try {
                    Object helloPacket = buildHelloPacket(helloPacketClass, helloCtor, playerName, profileId);
                    sendPacket.invoke(connection, helloPacket);
                } catch (Exception e) {
                    LOGGER.warn("[connection-bridge] failed to send hello packet", e);
                }

                // Set pending connection on Minecraft
                try {
                    Field pendingField = ForgeReflect.mcField(minecraftClass, "pendingConnection");
                    pendingField.setAccessible(true);
                    pendingField.set(fMinecraft, connection);
                } catch (NoSuchFieldException e) {
                    // Try alternate field names
                    try {
                        Field pendingField = ForgeReflect.mcField(minecraftClass, "field_71453_ak");
                        pendingField.setAccessible(true);
                        pendingField.set(fMinecraft, connection);
                    } catch (NoSuchFieldException e2) {
                        LOGGER.debug("[connection-bridge] no pendingConnection field");
                    }
                }

                LOGGER.info("[connection-bridge] initChannel complete, pipeline ready");
            }
        });

        EventLoop eventLoop = EVENT_LOOP_GROUP.next();
        eventLoop.register(rtcChannel).syncUninterruptibly();
        LOGGER.info("[connection-bridge] joinHost complete — connection established");
    }

    /**
     * Sets up the inline Netty pipeline for MC 1.13.2 packet serialization.
     *
     * @param pipeline     the Netty pipeline
     * @param clientbound  true for client-side (decodes CLIENTBOUND, encodes SERVERBOUND)
     */
    static void setupInlinePipeline(ChannelPipeline pipeline, boolean clientbound) throws Exception {
        String decodeDirection = clientbound ? "CLIENTBOUND" : "SERVERBOUND";
        String encodeDirection = clientbound ? "SERVERBOUND" : "CLIENTBOUND";

        // Resolve framing handlers
        Class<?> varintDecoderClass = resolveClass(
                "net.minecraft.network.Varint21FrameDecoder",
                "net.minecraft.network.VarintDecoder");
        Class<?> varintEncoderClass = resolveClass(
                "net.minecraft.network.Varint21FrameEncoder",
                "net.minecraft.network.VarintEncoder");

        // Resolve packet codec classes
        Class<?> packetDecoderClass = resolveClass(
                "net.minecraft.network.NettyPacketDecoder",
                "net.minecraft.network.PacketDecoder");
        Class<?> packetEncoderClass = resolveClass(
                "net.minecraft.network.NettyPacketEncoder",
                "net.minecraft.network.PacketEncoder");

        // Resolve EnumPacketDirection / NetworkDirection
        Class<?> directionClass = resolveClass(
                "net.minecraft.network.EnumPacketDirection",
                "net.minecraft.network.NetworkDirection");

        Object decodeDir = Enum.valueOf((Class<Enum>) directionClass, decodeDirection);
        Object encodeDir = Enum.valueOf((Class<Enum>) directionClass, encodeDirection);

        Object varintDecoder = varintDecoderClass.getConstructor().newInstance();
        Object varintEncoder = varintEncoderClass.getConstructor().newInstance();
        Object packetDecoder = packetDecoderClass.getConstructor(directionClass).newInstance(decodeDir);
        Object packetEncoder = packetEncoderClass.getConstructor(directionClass).newInstance(encodeDir);

        pipeline.addLast("splitter", (io.netty.channel.ChannelHandler) varintDecoder);
        pipeline.addLast("decoder", (io.netty.channel.ChannelHandler) packetDecoder);
        pipeline.addLast("prepender", (io.netty.channel.ChannelHandler) varintEncoder);
        pipeline.addLast("encoder", (io.netty.channel.ChannelHandler) packetEncoder);
    }

    private static Method resolveMethod(Class<?> owner, String primary, String fallback, Class<?>... paramTypes) throws NoSuchMethodException {
        try {
            return ForgeReflect.mcMethod(owner, primary, paramTypes);
        } catch (NoSuchMethodException e) {
            return ForgeReflect.mcMethod(owner, fallback, paramTypes);
        }
    }

    @SuppressWarnings("unchecked")
    private static Constructor<?> resolveHelloCtor(Class<?> helloPacketClass) throws Exception {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            return helloPacketClass.getConstructor(gameProfileClass);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Constructor<?> fallback = null;
            try { fallback = helloPacketClass.getConstructor(String.class, Optional.class); }
            catch (NoSuchMethodException e1) {
                try { fallback = helloPacketClass.getConstructor(String.class, UUID.class); }
                catch (NoSuchMethodException e2) {
                    fallback = helloPacketClass.getConstructors()[0];
                }
            }
            return fallback;
        }
    }

    private static Class<?> resolveClass(String primary, String fallback) throws ClassNotFoundException {
        try {
            return ForgeReflect.mcClass(primary);
        } catch (ClassNotFoundException e) {
            return ForgeReflect.mcClass(fallback);
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

    /**
     * Builds the login hello packet. 1.13.2 CLoginHelloPacket may take a
     * GameProfile, or (String, Optional<UUID>), or (String, UUID), or (String).
     */
    private static Object buildHelloPacket(Class<?> helloPacketClass, Constructor<?> helloCtor,
            String playerName, UUID profileId) throws Exception {
        Class<?>[] paramTypes = helloCtor.getParameterTypes();
        if (paramTypes.length == 1 && paramTypes[0].getName().equals("com.mojang.authlib.GameProfile")) {
            Object profile = paramTypes[0].getConstructor(UUID.class, String.class).newInstance(profileId, playerName);
            return helloCtor.newInstance(profile);
        }
        if (paramTypes.length == 2 && paramTypes[1] == Optional.class) {
            return helloCtor.newInstance(playerName, Optional.of(profileId));
        }
        if (paramTypes.length == 2 && paramTypes[1] == UUID.class) {
            return helloCtor.newInstance(playerName, profileId);
        }
        if (paramTypes.length == 1) {
            return helloCtor.newInstance(playerName);
        }
        return helloCtor.newInstance(playerName, profileId);
    }
}
