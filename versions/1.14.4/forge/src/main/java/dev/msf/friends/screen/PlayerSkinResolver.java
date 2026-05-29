package dev.msf.friends.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves player skins for MC 1.13.2 MCP.
 *
 * <p>In 1.13.2, the skin loading flow is:
 * <ol>
 *   <li>Fetch the GameProfile (with texture properties) from the session service
 *       using {@code fillProfileProperties()}</li>
 *   <li>Call {@link SkinManager#loadSkin(GameProfile, SkinManager.ISkinAvailableCallback, boolean)}
 *       to register the texture with the texture manager</li>
 *   <li>Use the callback to capture the resolved {@link ResourceLocation}</li>
 * </ol>
 *
 * <p>1.13.2 differences from 1.18.2: authlib uses fillProfileProperties() not fetchProfile().
 * Default skin is via {@link DefaultPlayerSkin}.
 */
final class PlayerSkinResolver {

    private PlayerSkinResolver() {}

    static CompletableFuture<ResourceLocation> fetchSkin(Minecraft minecraft, UUID profileId, String fallbackName) {
        GameProfile fallbackProfile = new GameProfile(profileId, fallbackName);
        CompletableFuture<ResourceLocation> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            GameProfile profile = fetchProfile(minecraft, fallbackProfile);

            minecraft.enqueue(() -> {
                try {
                    minecraft.getSkinManager().loadProfileTextures(profile, new SkinManager.ISkinAvailableCallback() {
                        @Override
                        public void onSkinTextureAvailable(MinecraftProfileTexture.Type type, ResourceLocation location, MinecraftProfileTexture texture) {
                            if (type == MinecraftProfileTexture.Type.SKIN) {
                                future.complete(location);
                            }
                        }
                    }, true);

                    minecraft.enqueue(() -> {
                        if (!future.isDone()) {
                            future.complete(DefaultPlayerSkin.getDefaultSkin(profileId));
                        }
                    });
                } catch (Throwable t) {
                    future.complete(DefaultPlayerSkin.getDefaultSkin(profileId));
                }
            });
        });

        CompletableFuture.delayedExecutor(5, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> {
                    if (!future.isDone()) {
                        future.complete(DefaultPlayerSkin.getDefaultSkin(profileId));
                    }
                });

        return future;
    }

    private static GameProfile fetchProfile(Minecraft minecraft, GameProfile fallbackProfile) {
        try {
            // authlib 1.5.25: fillProfileProperties(profile, requireSecure)
            minecraft.getSessionService().fillProfileProperties(fallbackProfile, true);
            if (fallbackProfile.getName() != null && !fallbackProfile.getName().isEmpty()) {
                return fallbackProfile;
            }
        } catch (Throwable ignored) {
        }
        return fallbackProfile;
    }
}
