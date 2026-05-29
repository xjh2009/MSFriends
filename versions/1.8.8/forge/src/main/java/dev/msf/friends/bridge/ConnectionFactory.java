package dev.msf.friends.bridge;

import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;

/**
 * Creates MC {@link NetworkManager} objects for MC 1.8.8.
 */
public final class ConnectionFactory {
    private ConnectionFactory() {}

    /**
     * Create an unbound {@link NetworkManager} whose channel will be set later.
     */
    public static NetworkManager createUnbound(EnumPacketDirection side) {
        return new NetworkManager(side);
    }
}
