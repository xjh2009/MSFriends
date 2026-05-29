package dev.msf.friends.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 1.14.4-compatible icon button.
 * Uses GlStateManager instead of RenderSystem, no MatrixStack.
 * ButtonWidget takes String in 1.14.4.
 */
public class IconButtonWidget extends ButtonWidget {
    private final @Nullable Identifier iconTexture;
    private final int iconWidth;
    private final int iconHeight;
    private final boolean iconOnly;

    public IconButtonWidget(int x, int y, int width, int height, String message, PressAction onPress,
                            @Nullable Identifier iconTexture, int iconWidth, int iconHeight) {
        this(x, y, width, height, message, onPress, iconTexture, iconWidth, iconHeight, true);
    }

    public IconButtonWidget(int x, int y, int width, int height, String message, PressAction onPress,
                            @Nullable Identifier iconTexture, int iconWidth, int iconHeight, boolean iconOnly) {
        super(x, y, width, height, message, onPress);
        this.iconTexture = iconTexture;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.iconOnly = iconOnly;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float delta) {
        String oldMessage = this.getMessage();
        if (this.iconOnly) {
            this.setMessage("");
        }
        super.renderButton(mouseX, mouseY, delta);
        if (this.iconOnly) {
            this.setMessage(oldMessage);
        }

        if (this.iconTexture != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.getTextureManager().bindTexture(this.iconTexture);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, this.alpha);
            GlStateManager.enableBlend();
            int iconX = this.x + (this.width - this.iconWidth) / 2;
            int iconY = this.y + (this.height - this.iconHeight) / 2;
            blit(iconX, iconY, 0, 0, this.iconWidth, this.iconHeight, this.iconWidth, this.iconHeight);
            GlStateManager.disableBlend();
        }
    }
}
