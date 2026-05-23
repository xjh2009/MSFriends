package dev.msf.friends.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Custom toast that displays a player's skin head alongside the notification
 * text. Mirrors 26.1.2's FriendToast style where the triggering player's
 * face is shown instead of a generic icon.
 *
 * <p>1.20.1 uses the old Toast API: {@code draw(DrawContext, ToastManager, long)}
 * returns {@code Visibility}. 26.1.2 uses the newer
 * {@code extractRenderState / update / getWantedVisibility} pattern.
 *
 * <p>Layout: [8px padding][20x20 face][6px gap][title / description][8px padding]
 */
public class FriendToast implements Toast {

    /** Face icon size in pixels. */
    private static final int FACE_SIZE = 20;
    /** Left padding before the face icon. */
    private static final int PADDING_LEFT = 8;
    /** Gap between face and text. */
    private static final int FACE_TEXT_GAP = 6;
    /** Right padding after text. */
    private static final int PADDING_RIGHT = 8;
    /** Top padding for text lines. */
    private static final int PADDING_TOP = 7;
    /** Spacing between title and description lines. */
    private static final int LINE_SPACING = 12;
    /** X offset where text starts. */
    private static final int TEXT_LEFT = PADDING_LEFT + FACE_SIZE + FACE_TEXT_GAP;

    private static final long DISPLAY_TIME_MS = 5000L;

    private final Text title;
    private final @Nullable Text description;
    private final @Nullable UUID profileId;

    /** Resolved skin texture — may be set asynchronously when the skin loads. */
    private volatile @Nullable Identifier resolvedSkinTexture;
    private boolean skinRequested;

    private long startTime;
    private boolean hasStarted;

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
    public Visibility draw(DrawContext context, ToastManager manager, long time) {
        if (!hasStarted) {
            this.startTime = time;
            this.hasStarted = true;
        }

        // Request skin if not already done
        if (!skinRequested && profileId != null) {
            skinRequested = true;
            requestSkin(manager.getClient(), profileId);
        }

        // Draw background
        context.drawTexture(new Identifier("minecraft", "textures/gui/toasts.png"),
                0, 0, 0, 0, this.getWidth(), this.getHeight());

        // Draw player face
        int faceX = PADDING_LEFT;
        int faceY = (this.getHeight() - FACE_SIZE) / 2;
        Identifier skinTexture = this.resolvedSkinTexture;
        if (skinTexture == null && profileId != null) {
            skinTexture = DefaultSkinHelper.getTexture(profileId);
        }
        PlayerSkinDrawer.draw(context, skinTexture, faceX, faceY, FACE_SIZE);

        // Draw text
        TextRenderer font = manager.getClient().textRenderer;
        int maxTextWidth = this.getWidth() - TEXT_LEFT - PADDING_RIGHT;

        if (description == null) {
            // Single-line: vertically centered
            List<OrderedText> titleLines = font.wrapLines(title, maxTextWidth);
            if (!titleLines.isEmpty()) {
                int textY = (this.getHeight() - font.fontHeight) / 2;
                context.drawText(font, titleLines.get(0), TEXT_LEFT, textY, 0xFFFFFFFF, false);
            }
        } else {
            // Two lines: title + description
            int y = PADDING_TOP;
            List<OrderedText> titleLines = font.wrapLines(title, maxTextWidth);
            if (!titleLines.isEmpty()) {
                context.drawText(font, titleLines.get(0), TEXT_LEFT, y, 0xFFFFFFFF, false);
            }
            y += LINE_SPACING;
            List<OrderedText> descLines = font.wrapLines(description, maxTextWidth);
            if (!descLines.isEmpty()) {
                context.drawText(font, descLines.get(0), TEXT_LEFT, y, 0xFFAAAAAA, false);
            }
        }

        // Check if we should hide
        long elapsed = time - this.startTime;
        if (elapsed >= DISPLAY_TIME_MS) {
            return Visibility.HIDE;
        }
        return Visibility.SHOW;
    }

    private void requestSkin(MinecraftClient minecraft, UUID profileId) {
        PlayerSkinResolver.fetchSkin(minecraft, profileId, "")
            .thenAccept(skin -> this.resolvedSkinTexture = skin);
    }

    /**
     * Convenience factory: show a friend toast via the ToastManager.
     */
    public static void show(Text title, @Nullable Text description, @Nullable UUID profileId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        FriendToast toast = new FriendToast(title, description, profileId);
        mc.getToastManager().add(toast);
    }
}
