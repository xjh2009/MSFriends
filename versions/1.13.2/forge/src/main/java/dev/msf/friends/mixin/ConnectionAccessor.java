package dev.msf.friends.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code channel} field on NetworkManager for RtcChannel type checks.
 * 1.13.2 MCP: Connection → NetworkManager.
 */
@Mixin(NetworkManager.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    Channel msf$getChannel();
}
