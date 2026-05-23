package dev.msf.friends.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Expose the protected {@code services} field of MinecraftServer so that
 * server-side mixins (e.g. ServerLoginMixin) can access the SessionService.
 */
@Mixin(MinecraftServer.class)
public interface ServerAccessor {

    @Accessor("services")
    Services msf$getServices();
}
