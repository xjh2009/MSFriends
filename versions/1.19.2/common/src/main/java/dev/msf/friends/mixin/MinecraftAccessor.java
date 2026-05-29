package dev.msf.friends.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code integratedServerConnection} field on MinecraftClient.
 * 1.19.2 Yarn: pendingConnection → integratedServerConnection.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftAccessor {
    @Accessor("integratedServerConnection")
    ClientConnection msf$getPendingConnection();

    @Accessor("integratedServerConnection")
    void msf$setPendingConnection(ClientConnection connection);
}
