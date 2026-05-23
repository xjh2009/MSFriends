package dev.msf.friends.compat;

import net.minecraft.resources.ResourceLocation;

/**
 * Compatibility bridge: MC 1.21.1 uses {@code ResourceLocation} while
 * MC 26.x renames it to {@code Identifier}.  This class provides the
 * 26.x-style API surface on top of 1.21.1's {@code ResourceLocation}.
 *
 * <p>Usage: replace {@code import net.minecraft.resources.Identifier}
 * with {@code import dev.msf.friends.compat.Identifier} and all
 * {@code ResourceLocation} references become the 26.x-style
 * {@code Identifier.fromNamespaceAndPath(...)} calls.
 */
public final class Identifier {

    private Identifier() {} // utility — do not instantiate

    /**
     * Alias for {@link ResourceLocation#fromNamespaceAndPath}.
     */
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /**
     * Alias for {@link ResourceLocation#withDefaultNamespace}.
     */
    public static ResourceLocation withDefaultNamespace(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
