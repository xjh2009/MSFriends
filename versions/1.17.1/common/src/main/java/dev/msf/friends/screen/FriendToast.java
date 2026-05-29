package dev.msf.friends.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

import static net.minecraft.client.gui.DrawableHelper.drawTexture;

/**
 * Custom toast for MC 1.17.1 Yarn.
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

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 32;
    }

    @Override
    public Toast.Visibility draw(MatrixStack matrixStack, ToastManager manager, long startTime) {
        if (this.firstDrawTime == 0L) {
            this.firstDrawTime = startTime;
        }

        if (!skinRequested && profileId != null) {
            skinRequested = true;
            requestSkin(manager.getGame(), profileId);
        }

        // Background
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        drawTexture(matrixStack, 0, 0, 0f, 0f, this.getWidth(), this.getHeight(), 256, 256);

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
        renderFace(matrixStack, skinLoc, faceX, faceY);

        // Text
        int textX = TEXT_LEFT;
        int titleY = PADDING_TOP;
        TextRenderer textRenderer = manager.getGame().textRenderer;
        textRenderer.draw(matrixStack, title, textX, titleY, 0xFFFFFFFF);

        if (description != null) {
            textRenderer.draw(matrixStack, description, textX, titleY + LINE_SPACING, 0xFFAAAAAA);
        }

        long elapsed = startTime - this.firstDrawTime;
        if (elapsed >= DISPLAY_TIME_MS) {
            return Toast.Visibility.HIDE;
        }
        return Toast.Visibility.SHOW;
    }

    private void renderFace(MatrixStack matrixStack, Identifier skinLoc, int x, int y) {
        RenderSystem.setShaderTexture(0, skinLoc);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        int s = FACE_SIZE;
        drawTexture(matrixStack, x, y, s, s, 8f, 8f, 8, 8, 64, 64);
        drawTexture(matrixStack, x, y, s, s, 40f, 8f, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private void requestSkin(MinecraftClient client, UUID id) {
        PlayerSkinResolver.fetchSkin(client, id, "")
                .thenAccept(resolved -> this.resolvedSkin = resolved);
    }

    public static void show(Text title, Text description, @Nullable UUID profileId) {
        MinecraftClient.getInstance().getToastManager().add(new FriendToast(title, description, profileId));
    }
}
