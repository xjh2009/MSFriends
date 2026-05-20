package dev.msf.friends.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Custom toast that displays a player's skin head alongside the notification
 * text. Mirrors 26.2's friend-related toast style where the triggering
 * player's face is shown instead of a generic icon.
 *
 * <p>Layout: [8px padding][20x20 face][6px gap][title / description][8px padding]
 */
public class FriendToast implements Toast {

    private static final Identifier BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("toast/system");

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

    private final Component title;
    private final @Nullable Component description;
    private final @Nullable UUID profileId;

    /** Resolved skin — may be set asynchronously when the skin loads. */
    private volatile @Nullable PlayerSkin resolvedSkin;
    private boolean skinRequested;

    private Toast.Visibility wantedVisibility = Toast.Visibility.SHOW;
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
    public Toast.Visibility getWantedVisibility() {
        return wantedVisibility;
    }

    @Override
    public void update(ToastManager toastManager, long time) {
        if (this.firstDrawTime == 0L) {
            this.firstDrawTime = time;
        }

        // Request skin if not already done
        if (!skinRequested && profileId != null) {
            skinRequested = true;
            requestSkin(toastManager.getMinecraft(), profileId);
        }

        double multiplier = toastManager.getNotificationDisplayTimeMultiplier();
        long elapsed = time - this.firstDrawTime;
        if (elapsed >= (long) (DISPLAY_TIME_MS * multiplier)) {
            this.wantedVisibility = Toast.Visibility.HIDE;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
        // Draw background (9-sliced toast sprite)
        graphics.blitSprite(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                BACKGROUND_SPRITE,
                0, 0, this.width(), this.height()
        );

        // Draw player face
        int faceX = PADDING_LEFT;
        int faceY = (this.height() - FACE_SIZE) / 2; // vertically centered
        PlayerSkin skin = this.resolvedSkin;
        if (skin == null && profileId != null) {
            skin = DefaultPlayerSkin.get(profileId);
        }
        if (skin == null) {
            skin = DefaultPlayerSkin.getDefaultSkin();
        }
        PlayerFaceExtractor.extractRenderState(graphics, skin, faceX, faceY, FACE_SIZE);

        // Draw text
        int textX = TEXT_LEFT;
        int maxTextWidth = this.width() - textX - PADDING_RIGHT;

        // Title line
        List<FormattedCharSequence> titleLines = font.split(title, maxTextWidth);
        if (description == null) {
            // Single-line: vertically centered
            if (!titleLines.isEmpty()) {
                graphics.text(font, titleLines.getFirst(), textX, (this.height() - font.lineHeight) / 2, 0xFFFFFFFF);
            }
        } else {
            // Two lines: title + description
            int y = PADDING_TOP;
            if (!titleLines.isEmpty()) {
                graphics.text(font, titleLines.getFirst(), textX, y, 0xFFFFFFFF);
            }
            y += LINE_SPACING;
            List<FormattedCharSequence> descLines = font.split(description, maxTextWidth);
            if (!descLines.isEmpty()) {
                graphics.text(font, descLines.getFirst(), textX, y, 0xFFAAAAAA);
            }
        }
    }

    private void requestSkin(Minecraft minecraft, UUID profileId) {
        PlayerSkinResolver.fetchSkin(minecraft, profileId, "")
            .thenAccept(skin -> this.resolvedSkin = skin);
    }

    /**
     * Convenience factory: show a friend toast via the ToastManager.
     */
    public static void show(Component title, @Nullable Component description, @Nullable UUID profileId) {
        Minecraft mc = Minecraft.getInstance();
        FriendToast toast = new FriendToast(title, description, profileId);
        mc.getToastManager().addToast(toast);
    }
}
