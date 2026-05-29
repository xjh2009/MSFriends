package dev.msf.friends.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraft.util.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * 1.14.4 Forge icon button that renders a centered texture icon.
 *
 * <p>Extends {@link GuiButtonExt} (Forge's concrete Button implementation).
 * Has IPressable callback via constructor. Uses getMessage()/setMessage()
 * from Widget parent. x, y, active, visible are public fields on Widget.
 */
public class IconButtonWidget extends GuiButtonExt {

    @FunctionalInterface
    public interface PressAction {
        void onPress(IconButtonWidget button);
    }

    private final @Nullable ResourceLocation iconTexture;
    private final int iconWidth;
    private final int iconHeight;
    private final boolean iconOnly;
    private final PressAction pressAction;

    public IconButtonWidget(int x, int y, int width, int height, String message,
                            PressAction onPress,
                            @Nullable ResourceLocation iconTexture, int iconWidth, int iconHeight) {
        this(x, y, width, height, message, onPress, iconTexture, iconWidth, iconHeight, true);
    }

    public IconButtonWidget(int x, int y, int width, int height, String message,
                            PressAction onPress,
                            @Nullable ResourceLocation iconTexture, int iconWidth, int iconHeight,
                            boolean iconOnly) {
        super(x, y, width, height, message, b -> {});
        this.pressAction = onPress;
        this.iconTexture = iconTexture;
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.iconOnly = iconOnly;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.pressAction.onPress(this);
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

        if (this.iconTexture != null && this.visible) {
            Minecraft.getInstance().getTextureManager().bindTexture(this.iconTexture);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            int iconX = this.x + (this.width - this.iconWidth) / 2;
            int iconY = this.y + (this.height - this.iconHeight) / 2;
            AbstractGui.blit(
                    iconX, iconY, 0f, 0f,
                    this.iconWidth, this.iconHeight,
                    this.iconWidth, this.iconHeight);
            GlStateManager.disableBlend();
        }
    }
}
