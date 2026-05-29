package dev.msf.friends.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code channel} field on ClientConnection for RtcChannel type checks.
 * 1.17.1 Yarn: Connection → ClientConnection.
 */
@Mixin(ClientConnection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    Channel msf$getChannel();
}
