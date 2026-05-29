package dev.msf.friends.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A horizontal-rule separator widget for OptionsList in MC 1.21.x.
 * Draws a thin horizontal line with an optional centered text label,
 * replacing the ugly two-button placeholder hack.
 */
public class SectionSeparator extends AbstractWidget {

    private static final int HEIGHT = 12;
    private static final int LINE_COLOR = 0xFF606060;

    public SectionSeparator(int x, int y, int width, Component label) {
        super(x, y, width, HEIGHT, label);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int midY = getY() + HEIGHT / 2;

        // Draw horizontal line across full width
        graphics.fill(getX(), midY, getX() + width, midY + 1, LINE_COLOR);

        // Draw centered label with background gap
        Component label = getMessage();
        if (label != null && !label.getString().isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            int textW = mc.font.width(label);
            int textX = getX() + (width - textW) / 2;
            int textY = getY() + (HEIGHT - mc.font.lineHeight) / 2;

            // Clear the line behind the text
            graphics.fill(textX - 4, midY, textX + textW + 4, midY + 1, 0x00000000);

            graphics.drawString(mc.font, label, textX, textY, 0xFFA0A0A0);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // No narration for decorative separators
    }

    @Override
    public boolean isActive() {
        return false; // Not interactable
    }
}
