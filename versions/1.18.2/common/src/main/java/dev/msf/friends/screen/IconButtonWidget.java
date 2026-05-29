package dev.msf.friends.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.components.Button;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * 1.18.2-compatible icon button.
 * Keeps the vanilla button background but renders only a centered texture icon.
 */
public class IconButtonWidget extends Button {
    private final @Nullable ResourceLocation iconTexture;
    private final int iconWidth;
    private final int iconHeight;
    private final boolean iconOnly;

    public IconButtonWidget(int x, int y, int width, int height, Component message, Button.OnPress onPress,
                            @Nullable ResourceLocation iconTexture, int iconWidth, int iconHeight) {
        this(x, y, width, height, message, onPress, iconTexture, iconWidth, iconHeight, true);
    }

    public IconButtonWidget(int x, int y, int width, int height, Component message, Button.OnPress onPress,
                            @Nullable ResourceLocation iconTexture, int iconWidth, int iconHeight, boolean iconOnly) {
        super(x, y, width, height, message, onPress);
        this.iconTexture = iconTexture;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.iconOnly = iconOnly;
    }

    @Override
    public void renderButton(PoseStack matrices, int mouseX, int mouseY, float delta) {
        Component oldMessage = this.getMessage();
        if (this.iconOnly) {
            this.setMessage(TextComponent.EMPTY);
        }
        super.renderButton(matrices, mouseX, mouseY, delta);
        if (this.iconOnly) {
            this.setMessage(oldMessage);
        }

        if (this.iconTexture != null) {
            RenderSystem.setShaderTexture(0, this.iconTexture);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
            RenderSystem.enableBlend();
            int iconX = this.x + (this.width - this.iconWidth) / 2;
            int iconY = this.y + (this.height - this.iconHeight) / 2;
            blit(matrices, iconX, iconY, 0, 0, this.iconWidth, this.iconHeight, this.iconWidth, this.iconHeight);
            RenderSystem.disableBlend();
        }
    }
}
