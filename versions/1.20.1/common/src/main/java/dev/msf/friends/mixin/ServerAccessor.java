package dev.msf.friends.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ApiServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Expose the protected {@code apiServices} field of MinecraftServer so that
 * server-side mixins (e.g. ServerLoginMixin) can access the
 * ApiServices record (sessionService, userCache, etc.) for P2P login.
 *
 * <p>Mirrors 26.1.2's ServerAccessor pattern and 1.21.1's implementation.
 * In 1.20.1 Yarn, the equivalent of 1.21.1's {@code Services} field is
 * {@code apiServices} of type {@code ApiServices}. It provides
 * {@code sessionService()} and {@code userCache()} accessors.
 */
@Mixin(MinecraftServer.class)
public interface ServerAccessor {

    @Accessor("apiServices")
    ApiServices msf$getApiServices();
}
