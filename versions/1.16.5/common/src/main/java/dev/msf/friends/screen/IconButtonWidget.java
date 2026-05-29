package dev.msf.friends.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 1.16.5-compatible icon button.
 * Keeps the vanilla button background but renders only a centered texture icon.
 *
 * 1.16.5 uses RenderSystem.bindTexture() instead of setShaderTexture().
 */
public class IconButtonWidget extends ButtonWidget {
    private final @Nullable Identifier iconTexture;
    private final int iconWidth;
    private final int iconHeight;
    private final boolean iconOnly;

    public IconButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress,
                            @Nullable Identifier iconTexture, int iconWidth, int iconHeight) {
        this(x, y, width, height, message, onPress, iconTexture, iconWidth, iconHeight, true);
    }

    public IconButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress,
                            @Nullable Identifier iconTexture, int iconWidth, int iconHeight, boolean iconOnly) {
        super(x, y, width, height, message, onPress);
        this.iconTexture = iconTexture;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.iconOnly = iconOnly;
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        Text oldMessage = this.getMessage();
        if (this.iconOnly) {
            this.setMessage(LiteralText.EMPTY);
        }
        super.renderButton(matrices, mouseX, mouseY, delta);
        if (this.iconOnly) {
            this.setMessage(oldMessage);
        }

        if (this.iconTexture != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.getTextureManager().bindTexture(this.iconTexture);
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, this.alpha);
            RenderSystem.enableBlend();
            int iconX = this.x + (this.width - this.iconWidth) / 2;
            int iconY = this.y + (this.height - this.iconHeight) / 2;
            drawTexture(matrices, iconX, iconY, 0, 0, this.iconWidth, this.iconHeight, this.iconWidth, this.iconHeight);
            RenderSystem.disableBlend();
        }
    }
}
