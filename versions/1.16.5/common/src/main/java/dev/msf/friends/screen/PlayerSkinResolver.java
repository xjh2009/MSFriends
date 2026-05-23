package dev.msf.friends.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves player skins for MC 1.16.5 Yarn.
 *
 * <p>In 1.16.5, the skin loading flow is:
 * <ol>
 *   <li>Create a GameProfile with the player UUID</li>
 *   <li>Fetch profile properties via reflection (1.16.5 Yarn does not expose
 *       getSessionService() directly on MinecraftClient)</li>
 *   <li>Call {@link PlayerSkinProvider#loadSkin(GameProfile, PlayerSkinProvider.SkinTextureAvailableCallback, boolean)}
 *       to register the texture with the texture manager</li>
 *   <li>Use the callback to capture the resolved {@link Identifier}</li>
 * </ol>
 */
final class PlayerSkinResolver {

    private PlayerSkinResolver() {}

    static CompletableFuture<Identifier> fetchSkin(MinecraftClient minecraft, UUID profileId, String fallbackName) {
        GameProfile fallbackProfile = new GameProfile(profileId, fallbackName);
        CompletableFuture<Identifier> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            GameProfile profile = fetchProfile(fallbackProfile);

            minecraft.execute(() -> {
                try {
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

    /**
     * Fetch a GameProfile with texture properties populated.
     * 1.16.5 Yarn does not directly expose getSessionService() on MinecraftClient,
     * so we use reflection to access the session service and fill profile properties.
     */
    private static GameProfile fetchProfile(GameProfile fallbackProfile) {
        try {
            // Use authlib's YggdrasilMinecraftSessionService directly via reflection
            // The session service is accessible through MinecraftClient's session
            var mc = MinecraftClient.getInstance();
            // Try to get the session service via the network handler's connection
            // or via the MinecraftServer if integrated server is running
            // Fallback: just pass the profile without properties — loadSkin will
            // still work for profiles with cached textures
            Object sessionService = mc.getSessionService();
            if (sessionService != null) {
                java.lang.reflect.Method fillMethod = sessionService.getClass()
                        .getMethod("fillProfileProperties", GameProfile.class, boolean.class);
                Object result = fillMethod.invoke(sessionService, fallbackProfile, true);
                if (result instanceof GameProfile) {
                    return (GameProfile) result;
                }
            }
        } catch (Throwable ignored) {
        }
        return fallbackProfile;
    }
}