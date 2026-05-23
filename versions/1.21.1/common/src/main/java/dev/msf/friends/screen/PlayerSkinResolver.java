package dev.msf.friends.screen;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PlayerSkinResolver {

    private PlayerSkinResolver() {}

    static CompletableFuture<PlayerSkin> fetchSkin(Minecraft minecraft, UUID profileId, String fallbackName) {
        GameProfile fallbackProfile = new GameProfile(profileId, fallbackName);
        return CompletableFuture
                .supplyAsync(() -> fetchProfile(minecraft, fallbackProfile))
                .thenCompose(profile -> minecraft.getSkinManager().getOrLoad(profile)
                        .thenApply(skin -> skin != null ? skin : DefaultPlayerSkin.get(profile.id())));
    }

    private static GameProfile fetchProfile(Minecraft minecraft, GameProfile fallbackProfile) {
        try {
            var result = minecraft.getMinecraftSessionService().fetchProfile(fallbackProfile.id(), true);
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