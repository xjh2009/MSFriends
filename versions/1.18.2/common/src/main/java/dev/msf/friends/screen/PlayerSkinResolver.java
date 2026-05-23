package dev.msf.friends.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves player skins for MC 1.18.2.
 * Uses SkinManager.registerSkins() with callback to obtain skin ResourceLocation.
 */
final class PlayerSkinResolver {

    private PlayerSkinResolver() {}

    private static final ConcurrentHashMap<UUID, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();

    static CompletableFuture<ResourceLocation> fetchSkin(Minecraft minecraft, UUID profileId, String fallbackName) {
        ResourceLocation cached = SKIN_CACHE.get(profileId);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        CompletableFuture<ResourceLocation> future = new CompletableFuture<>();

        // Step 1: Fetch profile async, then register skin on main thread
        CompletableFuture.supplyAsync(() -> fetchProfile(minecraft, profileId, fallbackName))
                .thenAccept(profile -> minecraft.execute(() -> registerSkin(minecraft, profile, profileId, future)));

        return future;
    }

    private static GameProfile fetchProfile(Minecraft minecraft, UUID profileId, String fallbackName) {
        try {
            var result = minecraft.getMinecraftSessionService().fetchProfile(profileId, true);
            if (result != null && result.profile() != null) {
                GameProfile fetched = result.profile();
                if (fetched.name() == null || fetched.name().isBlank()) {
                    return new GameProfile(profileId, fallbackName, fetched.properties());
                }
                return fetched;
            }
        } catch (Throwable ignored) {}
        return new GameProfile(profileId, fallbackName);
    }

    private static void registerSkin(Minecraft minecraft, GameProfile profile, UUID profileId, CompletableFuture<ResourceLocation> future) {
        try {
            minecraft.getSkinManager().registerSkins(profile, new SkinManager.SkinTextureCallback() {
                @Override
                public void onSkinTextureAvailable(MinecraftProfileTexture.Type type, ResourceLocation location, MinecraftProfileTexture profileTexture) {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        SKIN_CACHE.put(profileId, location);
                        future.complete(location);
                    }
                }
            }, true);
        } catch (Throwable t) {
            future.complete(DefaultPlayerSkin.getDefaultSkin(profileId));
        }
        // Fallback timeout: complete with default if callback never fires for SKIN type
        minecraft.execute(() -> {
            if (!future.isDone()) {
                SKIN_CACHE.putIfAbsent(profileId, DefaultPlayerSkin.getDefaultSkin(profileId));
                future.complete(DefaultPlayerSkin.getDefaultSkin(profileId));
            }
        });
    }

    static ResourceLocation getSkin(UUID profileId) {
        return SKIN_CACHE.getOrDefault(profileId, DefaultPlayerSkin.getDefaultSkin(profileId));
    }
}
