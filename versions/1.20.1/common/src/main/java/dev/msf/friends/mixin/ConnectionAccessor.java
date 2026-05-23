package dev.msf.friends.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code channel} field on ClientConnection for RtcChannel type checks.
 */
@Mixin(ClientConnection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    Channel msf$getChannel();
}
