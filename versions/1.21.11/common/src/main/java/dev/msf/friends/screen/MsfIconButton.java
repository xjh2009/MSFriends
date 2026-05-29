package dev.msf.friends.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A 20×20 icon-only button for MC 1.21.x (Mojang mappings).
 * Draws the standard button background and centers a sprite icon
 * without rendering any text, so the label never overlaps the icon.
 */
public class MsfIconButton extends Button {

    private final Identifier sprite;
    private final int spriteWidth;
    private final int spriteHeight;

    public MsfIconButton(int x, int y, int width, int height,
                         Identifier sprite, int spriteWidth, int spriteHeight,
                         Component narration, OnPress onPress) {
        super(x, y, width, height, narration, onPress, DEFAULT_NARRATION);
        this.sprite = sprite;
        this.spriteWidth = spriteWidth;
        this.spriteHeight = spriteHeight;
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw the standard button background sprite
        this.renderDefaultSprite(graphics);
        // Draw the icon centered in the button (no text)
        int iconX = getX() + (width - spriteWidth) / 2;
        int iconY = getY() + (height - spriteHeight) / 2;
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                sprite, iconX, iconY, spriteWidth, spriteHeight);
    }
}
