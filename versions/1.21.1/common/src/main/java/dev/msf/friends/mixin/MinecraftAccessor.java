package dev.msf.friends.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code pendingConnection} field on {@link Minecraft}
 * for P2P bridge code that needs to set the pending connection.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("pendingConnection")
    @Nullable Connection msf$getPendingConnection();

    @Accessor("pendingConnection")
    void msf$setPendingConnection(@Nullable Connection connection);
}
