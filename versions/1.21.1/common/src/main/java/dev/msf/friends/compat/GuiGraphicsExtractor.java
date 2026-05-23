package dev.msf.friends.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;

/**
 * Compatibility bridge: MC 26.x introduces {@code GuiGraphicsExtractor}
 * as the render-state extraction API while MC 1.21.1 uses
 * {@code GuiGraphics} directly.  This wrapper delegates every call to
 * the underlying {@code GuiGraphics}, giving the 1.21.1 source tree
 * the same 26.x-style API surface.
 */
public final class GuiGraphicsExtractor {

    private final GuiGraphics delegate;

    public GuiGraphicsExtractor(GuiGraphics delegate) {
        this.delegate = delegate;
    }

    /** Return the underlying {@code GuiGraphics} for APIs not covered here. */
    public GuiGraphics unwrap() {
        return delegate;
    }

    // ---- blitSprite ----

    public void blitSprite(ResourceLocation sprite, int x, int y, int width, int height) {
        delegate.blitSprite(sprite, x, y, width, height);
    }

    // ---- text drawing ----

    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        delegate.drawString(font, text, x, y, color);
    }

    public void text(Font font, Component text, int x, int y, int color) {
        delegate.drawString(font, text, x, y, color);
    }

    public void text(Font font, String text, int x, int y, int color) {
        delegate.drawString(font, text, x, y, color);
    }

    public void centeredText(Font font, FormattedCharSequence text, int x, int y, int color) {
        delegate.drawCenteredString(font, text, x, y, color);
    }

    public void centeredText(Font font, Component text, int x, int y, int color) {
        delegate.drawCenteredString(font, text, x, y, color);
    }

    public void centeredText(Font font, String text, int x, int y, int color) {
        delegate.drawCenteredString(font, text, x, y, color);
    }

    // ---- fill ----

    public void fill(int x1, int y1, int x2, int y2, int color) {
        delegate.fill(x1, y1, x2, y2, color);
    }
}
