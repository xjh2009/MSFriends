package dev.msf.friends.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code channel} field on Connection for RtcChannel type checks.
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    Channel msf$getChannel();
}
