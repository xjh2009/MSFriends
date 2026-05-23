package dev.msf.friends.screen;

import com.mojang.authlib.GameProfile;
import dev.msf.friends.util.ProfileHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves a player's skin texture Identifier from their UUID.
 *
 * <p>1.20.1 Yarn: skin returns {@code Identifier} (not PlayerSkin record).
 * Uses ProfileHelper for authlib 4.x compatibility.
 *
 * <p>Functionally equivalent to 26.1.2's PlayerSkinResolver which returns PlayerSkin.
 */
final class PlayerSkinResolver {

    private PlayerSkinResolver() {}

    static CompletableFuture<Identifier> fetchSkin(MinecraftClient minecraft, UUID profileId, String fallbackName) {
        GameProfile fallbackProfile = new GameProfile(profileId, fallbackName);
        return CompletableFuture
                .supplyAsync(() -> fetchProfile(minecraft, fallbackProfile))
                .thenApply(profile -> {
                    if (profile != null) {
                        Identifier skin = minecraft.getSkinProvider().loadSkin(profile);
                        if (skin != null) return skin;
                    }
                    return DefaultSkinHelper.getTexture(profileId);
                });
    }

    private static GameProfile fetchProfile(MinecraftClient minecraft, GameProfile fallbackProfile) {
        try {
            Object sessionService = minecraft.getSessionService();
            GameProfile filled = ProfileHelper.fillProfile(sessionService, fallbackProfile);
            if (filled != null) {
                if (filled.name() == null || filled.name().isBlank()) {
                    return new GameProfile(fallbackProfile.id(), fallbackProfile.name(), filled.properties());
                }
                return filled;
            }
        } catch (Throwable ignored) {
        }
        return fallbackProfile;
    }
}
