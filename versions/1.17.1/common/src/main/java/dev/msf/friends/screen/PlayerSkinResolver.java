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
 * Resolves player skins for MC 1.17.1 Yarn.
 * authlib 3.x uses fillProfileProperties() not fetchProfile().
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
            var result = sessionService.fetchProfile(fallbackProfile.id(), true);
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
