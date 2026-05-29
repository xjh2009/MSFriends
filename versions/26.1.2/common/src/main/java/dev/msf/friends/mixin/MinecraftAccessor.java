package dev.msf.friends.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes private/final fields on Minecraft for direct access (replacing reflection).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAccessor {
    @Shadow @Mutable
    private Connection pendingConnection;

    /** Setter exposed for use by bridge code (cast via Object to access). */
    public void msf$setPendingConnection(Connection connection) {
        this.pendingConnection = connection;
    }
}
