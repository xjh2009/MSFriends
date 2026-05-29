package dev.msf.friends.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 1.19.2-compatible icon button.
 * Keeps the vanilla button background but renders only a centered texture icon.
 */
public class IconButtonWidget extends ButtonWidget {
    private final @Nullable Identifier iconTexture;
    private final int iconW;
    private final int iconH;
    private final boolean iconOnly;
    private final int btnX;
    private final int btnY;
    private final int btnW;
    private final int btnH;

    public IconButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress,
                            @Nullable Identifier iconTexture, int iconWidth, int iconHeight) {
        this(x, y, width, height, message, onPress, iconTexture, iconWidth, iconHeight, true);
    }

    public IconButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress,
                            @Nullable Identifier iconTexture, int iconWidth, int iconHeight, boolean iconOnly) {
        super(x, y, width, height, message, onPress);
        this.iconTexture = iconTexture;
        this.iconW = iconWidth;
        this.iconH = iconHeight;
        this.iconOnly = iconOnly;
        this.btnX = x;
        this.btnY = y;
        this.btnW = width;
        this.btnH = height;
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.renderButton(matrices, mouseX, mouseY, delta);

        if (this.iconTexture != null) {
            RenderSystem.setShaderTexture(0, this.iconTexture);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            int iconX = this.btnX + (this.btnW - this.iconW) / 2;
            int iconY = this.btnY + (this.btnH - this.iconH) / 2;
            drawTexture(matrices, iconX, iconY, 0, 0, this.iconW, this.iconH, this.iconW, this.iconH);
            RenderSystem.disableBlend();
        }
    }
}
