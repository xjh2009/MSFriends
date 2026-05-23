package dev.msf.friends.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A button that displays a texture icon alongside (or instead of) text.
 * Replaces 26.1.2's {@code SpriteIconButton} which doesn't exist in 1.20.1.
 *
 * <p>In 1.20.1 there is no sprite-based button system, so we draw a simple
 * texture icon centered in the button area, with optional text to the right.
 * The icon is drawn at the specified size, centered vertically and
 * horizontally within the button.
 */
public class IconButtonWidget extends ButtonWidget {
    private final @Nullable Identifier iconTexture;
    private final int iconWidth;
    private final int iconHeight;
    private final boolean iconOnly;

    private IconButtonWidget(int x, int y, int width, int height,
                            Text message, PressAction onPress,
                            @Nullable Identifier iconTexture,
                            int iconWidth, int iconHeight,
                            boolean iconOnly) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.iconTexture = iconTexture;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.iconOnly = iconOnly;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw standard button background
        super.renderButton(context, mouseX, mouseY, delta);

        if (iconTexture != null) {
            // Center the icon in the button
            int iconX = this.getX() + (this.width - iconWidth) / 2;
            int iconY = this.getY() + (this.height - iconHeight) / 2;
            context.drawTexture(iconTexture, iconX, iconY, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
        }

        // If not icon-only, draw text (handled by super for standard buttons)
        if (!iconOnly) {
            // Text is already drawn by super.renderButton
        }
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        // Same as vanilla button sound
        super.playDownSound(soundManager);
    }

    /**
     * Create a builder for an IconButtonWidget.
     */
    public static Builder builder(Text message, PressAction onPress,
                                   Identifier iconTexture, int iconWidth, int iconHeight) {
        return new Builder(message, onPress, iconTexture, iconWidth, iconHeight);
    }

    /**
     * Create a builder for an icon-only IconButtonWidget (no text visible).
     */
    public static Builder builder(Text message, PressAction onPress, boolean iconOnly,
                                   Identifier iconTexture, int iconWidth, int iconHeight) {
        return new Builder(message, onPress, iconTexture, iconWidth, iconHeight).iconOnly(iconOnly);
    }

    public static class Builder {
        private final Text message;
        private final PressAction onPress;
        private final Identifier iconTexture;
        private final int iconWidth;
        private final int iconHeight;
        private boolean iconOnly = true;
        private int x, y, width = 20, height = 20;

        Builder(Text message, PressAction onPress, Identifier iconTexture, int iconWidth, int iconHeight) {
            this.message = message;
            this.onPress = onPress;
            this.iconTexture = iconTexture;
            this.iconWidth = iconWidth;
            this.iconHeight = iconHeight;
        }

        public Builder iconOnly(boolean iconOnly) { this.iconOnly = iconOnly; return this; }
        public Builder dimensions(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height; return this;
        }
        public Builder width(int width) { this.width = width; return this; }
        public Builder pos(int x, int y) { this.x = x; this.y = y; return this; }

        public IconButtonWidget build() {
            return new IconButtonWidget(x, y, width, height, message, onPress,
                    iconTexture, iconWidth, iconHeight, iconOnly);
        }
    }
}
