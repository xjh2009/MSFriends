package dev.msf.friends.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.msf.friends.util.Logging;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Custom toast that displays a player's skin head alongside the notification
 * text. Mirrors 26.2's friend-related toast style.
 *
 * <p>1.16.5 Yarn: toast background uses toasts.png texture atlas.
 * Extends DrawableHelper to access fill()/drawTexture().
 * Player skin face rendered using manual drawTexture calls
 * (1.16.5 has no PlayerSkinDrawer helper).
 *
 * <p>NOTE: 1.16.5 does NOT have RenderSystem.setShaderColor / setShaderTexture.
 * Use TextureManager.bindTexture() + direct drawTexture() instead.
 */
public class FriendToast extends DrawableHelper implements Toast {
    private static final Logger LOGGER = Logging.get();

    // 1.16.5: toast background is in toasts.png texture atlas
    private static final Identifier BACKGROUND_TEXTURE =
            new Identifier("minecraft", "textures/gui/toasts.png");

    private static final int FACE_SIZE = 20;
    private static final int PADDING_LEFT = 8;
    private static final int FACE_TEXT_GAP = 6;
    private static final int PADDING_RIGHT = 8;
    private static final int PADDING_TOP = 7;
    private static final int LINE_SPACING = 12;
    private static final int TEXT_LEFT = PADDING_LEFT + FACE_SIZE + FACE_TEXT_GAP;

    private final Text title;
    private final @Nullable Text description;
    private final @Nullable UUID profileId;

    private volatile @Nullable Identifier resolvedSkin;
    private boolean skinRequested;
    private long firstDrawTime;

    public FriendToast(Text title, @Nullable Text description, @Nullable UUID profileId) {
        this.title = title;
        this.description = description;
        this.profileId = profileId;
    }

    @Override
    public int getWidth() { return 160; }

    @Override
    public int getHeight() { return 32; }

    @Override
    public Toast.Visibility draw(MatrixStack matrices, ToastManager manager, long startTime) {
        if (this.firstDrawTime == 0L) {
            this.firstDrawTime = startTime;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        // Request skin if not already done
        if (!skinRequested && profileId != null) {
            skinRequested = true;
            requestSkin(mc, profileId);
        }

        // Background — toasts.png atlas (1.16.5 binds texture via TextureManager)
        mc.getTextureManager().bindTexture(BACKGROUND_TEXTURE);
        drawTexture(matrices, 0, 0, 0f, 0f, this.getWidth(), this.getHeight(), 256, 256);

        // Face — render player skin face using manual drawTexture
        int faceX = PADDING_LEFT;
        int faceY = (this.getHeight() - FACE_SIZE) / 2;
        Identifier skinLoc = this.resolvedSkin;
        if (skinLoc != null) {
            renderFace(matrices, skinLoc, faceX, faceY);
        } else {
            // Fallback: dark gray placeholder
            this.fill(matrices, faceX, faceY, faceX + FACE_SIZE, faceY + FACE_SIZE, 0xFF555555);
        }

        // Text
        int textX = TEXT_LEFT;
        int maxTextWidth = this.getWidth() - textX - PADDING_RIGHT;

        if (description == null) {
            // Single-line: vertically centered
            mc.textRenderer.draw(matrices, title, textX, (this.getHeight() - mc.textRenderer.fontHeight) / 2, 0xFFFFFFFF);
        } else {
            mc.textRenderer.draw(matrices, title, textX, PADDING_TOP, 0xFFFFFFFF);
            mc.textRenderer.draw(matrices, description, textX, PADDING_TOP + LINE_SPACING, 0xFFAAAAAA);
        }

        long elapsed = startTime - this.firstDrawTime;
        return elapsed < 5000L ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    private void renderFace(MatrixStack matrices, Identifier skinLoc, int x, int y) {
        // 1.16.5: bind texture via TextureManager (no RenderSystem.setShaderTexture)
        MinecraftClient.getInstance().getTextureManager().bindTexture(skinLoc);
        GlStateManager.color4f(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        int s = FACE_SIZE;
        // drawTexture(MatrixStack, x, y, width, height, u, v, regionW, regionH, texW, texH)
        drawTexture(matrices, x, y, s, s, 8f, 8f, 8, 8, 64, 64);
        drawTexture(matrices, x, y, s, s, 40f, 8f, 8, 8, 64, 64);
        GlStateManager.disableBlend();
    }

    private void requestSkin(MinecraftClient client, UUID profileId) {
        PlayerSkinResolver.fetchSkin(client, profileId, "")
                .thenAccept(resolved -> this.resolvedSkin = resolved);
    }

    public static void show(Text title, @Nullable Text description, @Nullable UUID profileId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.getToastManager().add(new FriendToast(title, description, profileId));
        }
    }
}