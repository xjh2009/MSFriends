package dev.msf.friends.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Compatibility bridge: MC 26.x uses {@code PlayerFaceExtractor.extractRenderState}
 * while MC 1.21.1 uses {@code PlayerFaceRenderer.draw}.  This class provides the
 * 26.x-style API on top of 1.21.1's renderer.
 */
public final class PlayerFaceExtractor {

    private PlayerFaceExtractor() {} // utility

    /**
     * Extract (draw) a player face — delegates to
     * {@link PlayerFaceRenderer#draw(GuiGraphics, ResourceLocation, int, int, int)}.
     *
     * @param g      the GuiGraphics (or GuiGraphicsExtractor.unwrap())
     * @param skin   the player skin
     * @param x      top-left x
     * @param y      top-left y
     * @param size   face size in pixels
     */
    public static void extractRenderState(GuiGraphics g, PlayerSkin skin, int x, int y, int size) {
        ResourceLocation texture = skin != null ? skin.texture() : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
        PlayerFaceRenderer.draw(g, texture, x, y, size);
    }
}
