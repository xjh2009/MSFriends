package dev.msf.friends.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves player skins for MC 1.14.4 Yarn.
 *
 * 1.14.4 uses the same skin loading flow as 1.16.5.
 */
final class PlayerSkinResolver {

    private PlayerSkinResolver() {}

    static CompletableFuture<Identifier> fetchSkin(MinecraftClient minecraft, UUID profileId, String fallbackName) {
        GameProfile fallbackProfile = new GameProfile(profileId, fallbackName);
        CompletableFuture<Identifier> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            GameProfile profile = fetchProfile(minecraft, fallbackProfile);

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

                    // If no skin texture property, loadSkin won't call back.
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

        // Fallback timeout
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
            var sessionService = minecraft.getSessionService();
            try {
                Method fetch = sessionService.getClass().getMethod("fetchProfile", UUID.class, boolean.class);
                Object result = fetch.invoke(sessionService, fallbackProfile.getId(), true);
                if (result != null) {
                    Method profile = result.getClass().getMethod("profile");
                    return (GameProfile) profile.invoke(result);
                }
            } catch (NoSuchMethodException ignored) {}
            try {
                Method fill = sessionService.getClass().getMethod("fillProfileProperties", GameProfile.class, boolean.class);
                return (GameProfile) fill.invoke(sessionService, fallbackProfile, true);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {
        }
        return fallbackProfile;
    }
}
