package dev.msf.friends.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves player skins for MC 1.19.2 Yarn.
 *
 * <p>In 1.19.2, the skin loading flow is:
 * <ol>
 *   <li>Fetch the GameProfile (with texture properties) from the session service</li>
 *   <li>Call {@link PlayerSkinProvider#loadSkin(GameProfile, PlayerSkinProvider.SkinTextureAvailableCallback, boolean)}
 *       to register the texture with the texture manager</li>
 *   <li>Use the callback to capture the resolved {@link Identifier}</li>
 * </ol>
 */
final class PlayerSkinResolver {

    private PlayerSkinResolver() {}

    /**
     * Fetch and resolve a player's skin texture.
     * Returns a CompletableFuture that completes with the skin Identifier
     * (or the default skin if resolution fails).
     */
    static CompletableFuture<Identifier> fetchSkin(MinecraftClient minecraft, UUID profileId, String fallbackName) {
        GameProfile fallbackProfile = new GameProfile(profileId, fallbackName);
        CompletableFuture<Identifier> future = new CompletableFuture<>();

        // Step 1: Fetch the profile (with texture properties) on a background thread
        CompletableFuture.runAsync(() -> {
            GameProfile profile = fetchProfile(minecraft, fallbackProfile);

            // Step 2: Load the skin on the render thread (texture operations must be on render thread)
            minecraft.execute(() -> {
                try {
                    // PlayerSkinProvider.loadSkin() will call the callback when the texture is registered
                    minecraft.getSkinProvider().loadSkin(profile, new PlayerSkinProvider.SkinTextureAvailableCallback() {
                        @Override
                        public void onSkinTextureAvailable(MinecraftProfileTexture.Type type, Identifier identifier, MinecraftProfileTexture texture) {
                            if (type == MinecraftProfileTexture.Type.SKIN) {
                                future.complete(identifier);
                            }
                        }
                    }, true);

                    // If the profile has no skin texture property, loadSkin won't call back.
                    // Complete with default skin after a short delay.
                    minecraft.execute(() -> {
                        if (!future.isDone()) {
                            future.complete(DefaultSkinHelper.getTexture(profileId));
                        }
                    });
                } catch (Throwable t) {
                    future.complete(DefaultSkinHelper.getTexture(profileId));
                }
            });
        });

        // Fallback timeout: if skin takes too long, use default
        CompletableFuture.delayedExecutor(5, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> {
                    if (!future.isDone()) {
                        future.complete(DefaultSkinHelper.getTexture(profileId));
                    }
                });

        return future;
    }

    private static GameProfile fetchProfile(MinecraftClient minecraft, GameProfile fallbackProfile) {
        try {
            // 1.19.2 Yarn: getSessionService() returns MinecraftSessionService
            var result = minecraft.getSessionService().fetchProfile(fallbackProfile.id(), true);
            if (result != null && result.profile() != null) {
                GameProfile fetched = result.profile();
                if (fetched.name() == null || fetched.name().isBlank()) {
                    return new GameProfile(fallbackProfile.id(), fallbackProfile.name(), fetched.properties());
                }
                return fetched;
            }
        } catch (Throwable ignored) {
        }
        return fallbackProfile;
    }
}