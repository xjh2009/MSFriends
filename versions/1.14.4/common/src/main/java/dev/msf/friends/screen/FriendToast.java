package dev.msf.friends.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

import static net.minecraft.client.gui.DrawableHelper.blit;

/**
 * Custom toast for MC 1.14.4 that displays a player's skin head alongside
 * the notification text.
 *
 * 1.14.4 Yarn Toast API: draw(ToastManager, long) — no MatrixStack.
 */
public class FriendToast implements Toast {

    private static final Identifier BACKGROUND_TEXTURE =
            new Identifier("minecraft", "textures/gui/toasts.png");

    private static final int FACE_SIZE = 20;
    private static final int PADDING_LEFT = 8;
    private static final int FACE_TEXT_GAP = 6;
    private static final int PADDING_TOP = 7;
    private static final int LINE_SPACING = 12;
    private static final int TEXT_LEFT = PADDING_LEFT + FACE_SIZE + FACE_TEXT_GAP;
    private static final long DISPLAY_TIME_MS = 5000L;

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

    private int getWidth() {
        return 160;
    }

    private int getHeight() {
        return 32;
    }

    @Override
    public Toast.Visibility draw(ToastManager manager, long startTime) {
        if (this.firstDrawTime == 0L) {
            this.firstDrawTime = startTime;
        }

        if (!skinRequested && profileId != null) {
            skinRequested = true;
            requestSkin(manager.getGame(), profileId);
        }

        // Background
        manager.getGame().getTextureManager().bindTexture(BACKGROUND_TEXTURE);
        blit(0, 0, 0f, 0f, this.getWidth(), this.getHeight(), 256, 256);

        // Face
        int faceX = PADDING_LEFT;
        int faceY = (this.getHeight() - FACE_SIZE) / 2;
        Identifier skinLoc = this.resolvedSkin;
        if (skinLoc == null && profileId != null) {
            skinLoc = DefaultSkinHelper.getTexture(profileId);
        }
        if (skinLoc == null) {
            skinLoc = DefaultSkinHelper.getTexture();
        }
        renderFace(skinLoc, faceX, faceY);

        // Text
        int textX = TEXT_LEFT;
        int titleY = PADDING_TOP;
        TextRenderer textRenderer = manager.getGame().textRenderer;
        textRenderer.draw(title.asString(), textX, titleY, 0xFFFFFFFF);

        if (description != null) {
            textRenderer.draw(description.asString(), textX, titleY + LINE_SPACING, 0xFFAAAAAA);
        }

        long elapsed = startTime - this.firstDrawTime;
        if (elapsed >= DISPLAY_TIME_MS) {
            return Toast.Visibility.HIDE;
        }
        return Toast.Visibility.SHOW;
    }

    private void renderFace(Identifier skinLoc, int x, int y) {
        MinecraftClient.getInstance().getTextureManager().bindTexture(skinLoc);
        GlStateManager.color4f(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        int s = FACE_SIZE;
        blitScaled(x, y, s, s, 8f, 8f, 8, 8, 64, 64);
        blitScaled(x, y, s, s, 40f, 8f, 8, 8, 64, 64);
        GlStateManager.disableBlend();
    }

    /** Draws a source texture region scaled to a different destination size. */
    private static void blitScaled(int x, int y, int destW, int destH,
                                   float u, float v, int srcW, int srcH, int texW, int texH) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef(x, y, 0);
        GlStateManager.scalef((float) destW / srcW, (float) destH / srcH, 1.0f);
        blit(0, 0, u, v, srcW, srcH, texW, texH);
        GlStateManager.popMatrix();
    }

    private void requestSkin(MinecraftClient client, UUID id) {
        PlayerSkinResolver.fetchSkin(client, id, "")
                .thenAccept(resolved -> this.resolvedSkin = resolved);
    }

    public static void show(Text title, Text description, @Nullable UUID profileId) {
        MinecraftClient.getInstance().getToastManager().add(new FriendToast(title, description, profileId));
    }
}
