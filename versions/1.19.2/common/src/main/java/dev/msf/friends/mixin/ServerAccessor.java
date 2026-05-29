package dev.msf.friends.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerNetworkIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code networkIo} field on MinecraftServer.
 * 1.19.2 Yarn: connection → networkIo.
 */
@Mixin(MinecraftServer.class)
public interface ServerAccessor {
    @Accessor("networkIo")
    ServerNetworkIo msf$getConnection();
}
