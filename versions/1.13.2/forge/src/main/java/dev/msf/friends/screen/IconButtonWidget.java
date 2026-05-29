package dev.msf.friends.screen;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraft.util.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1.13.2 Forge icon button that renders a centered texture icon.
 *
 * <p>Extends {@link GuiButtonExt} (Forge's concrete GuiButton implementation).
 * 1.13.2 GuiButtonExt has no IPressable callback, so we store a callback and
 * override {@link #onClick(double, double)}.
 *
 * <p>In 1.13.2, {@code render(int,int,float)} replaces 1.14+ {@code drawButton},
 * and {@code displayString} replaces {@code getMessage()/setMessage()}.
 */
public class IconButtonWidget extends GuiButtonExt {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(20000);

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
        super(ID_COUNTER.getAndIncrement(), x, y, width, height, message);
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
    public void render(int mouseX, int mouseY, float delta) {
        String oldMessage = this.displayString;
        if (this.iconOnly) {
            this.displayString = "";
        }
        super.render(mouseX, mouseY, delta);
        if (this.iconOnly) {
            this.displayString = oldMessage;
        }

        if (this.iconTexture != null && this.visible) {
            Minecraft.getInstance().getTextureManager().bindTexture(this.iconTexture);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            int iconX = this.x + (this.width - this.iconWidth) / 2;
            int iconY = this.y + (this.height - this.iconHeight) / 2;
            net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
                    iconX, iconY, 0, 0,
                    this.iconWidth, this.iconHeight,
                    this.iconWidth, this.iconHeight,
                    this.iconWidth, this.iconHeight);
            GlStateManager.disableBlend();
        }
    }
}
