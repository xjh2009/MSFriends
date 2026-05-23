package dev.msf.friends.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Custom toast for MC 1.18.2 that displays a player's skin head alongside
 * the notification text.
 *
 * <p>1.18.2 Mojmap Toast API: single {@code render(PoseStack, ToastComponent, long)}
 * method returning Visibility. Background uses toast.png texture atlas.
 */
public class FriendToast implements Toast {

    private static final ResourceLocation BACKGROUND_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/toasts.png");

    private static final int FACE_SIZE = 20;
    private static final int PADDING_LEFT = 8;
    private static final int FACE_TEXT_GAP = 6;
    private static final int PADDING_RIGHT = 8;
    private static final int PADDING_TOP = 7;
    private static final int LINE_SPACING = 12;
    private static final int TEXT_LEFT = PADDING_LEFT + FACE_SIZE + FACE_TEXT_GAP;
    private static final long DISPLAY_TIME_MS = 5000L;

    private final Component title;
    private final @Nullable Component description;
    private final @Nullable UUID profileId;

    private volatile @Nullable ResourceLocation resolvedSkin;
    private boolean skinRequested;
    private long firstDrawTime;

    public FriendToast(Component title, @Nullable Component description, @Nullable UUID profileId) {
        this.title = title;
        this.description = description;
        this.profileId = profileId;
    }

    @Override
    public int width() {
        return 160;
    }

    @Override
    public int height() {
        return 32;
    }

    @Override
    public Visibility render(PoseStack matrices, ToastComponent component, long time) {
        if (this.firstDrawTime == 0L) {
            this.firstDrawTime = time;
        }

        if (!skinRequested && profileId != null) {
            skinRequested = true;
            PlayerSkinResolver.fetchSkin(component.getMinecraft(), profileId, "")
                    .thenAccept(resolved -> this.resolvedSkin = resolved);
        }

        // Background — toasts.png atlas (1.18.2: blit is an instance method on GuiComponent)
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        // ToastComponent extends GuiComponent which has the blit method
        component.blit(matrices, 0, 0, 0, 0, this.width(), this.height());

        // Face
        int faceX = PADDING_LEFT;
        int faceY = (this.height() - FACE_SIZE) / 2;
        ResourceLocation skinLoc = this.resolvedSkin;
        if (skinLoc != null) {
            renderFace(matrices, component, skinLoc, faceX, faceY);
        }

        // Text
        Font font = component.getMinecraft().font;
        int textX = TEXT_LEFT;
        int maxTextWidth = this.width() - textX - PADDING_RIGHT;

        if (description == null) {
            List<FormattedCharSequence> lines = font.split(title, maxTextWidth);
            if (!lines.isEmpty()) font.draw(matrices, lines.get(0), textX, (this.height() - font.lineHeight) / 2, 0xFFFFFFFF);
        } else {
            List<FormattedCharSequence> titleLines = font.split(title, maxTextWidth);
            int y = PADDING_TOP;
            if (!titleLines.isEmpty()) font.draw(matrices, titleLines.get(0), textX, y, 0xFFFFFFFF);
            y += LINE_SPACING;
            List<FormattedCharSequence> descLines = font.split(description, maxTextWidth);
            if (!descLines.isEmpty()) font.draw(matrices, descLines.get(0), textX, y, 0xFFAAAAAA);
        }

        return time - this.firstDrawTime >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    private void renderFace(PoseStack matrices, ToastComponent component, ResourceLocation skinLoc, int x, int y) {
        RenderSystem.setShaderTexture(0, skinLoc);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        int s = FACE_SIZE;
        // 1.18.2: blit is an instance method on GuiComponent; ToastComponent inherits it
        component.blit(matrices, x, y, s, s, 8.0F, 8.0F, 8, 8, 64, 64);
        component.blit(matrices, x, y, s, s, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    public static void show(Component title, @Nullable Component description, @Nullable UUID profileId) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().addToast(new FriendToast(title, description, profileId));
    }
}
