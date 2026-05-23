package dev.msf.friends.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import dev.msf.friends.compat.GuiGraphicsExtractor;
import dev.msf.friends.compat.Identifier;
import dev.msf.friends.compat.PlayerFaceExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.resources.PlayerSkin;
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

    private static final ResourceLocation BACKGROUND_SPRITE =
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
    public Toast.Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long time) {
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(graphics);
        // Draw background (9-sliced toast sprite)
        g.blitSprite(
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
        PlayerFaceExtractor.extractRenderState(graphics, skin, faceX, faceY, FACE_SIZE);

        // Request skin if not already done
        if (!skinRequested && profileId != null) {
            skinRequested = true;
            requestSkin(toastComponent.getMinecraft(), profileId);
        }

        // Draw text
        Font font = toastComponent.getMinecraft().font;
        int textX = TEXT_LEFT;
        int maxTextWidth = this.width() - textX - PADDING_RIGHT;

        // Title line
        List<FormattedCharSequence> titleLines = font.split(title, maxTextWidth);
        if (description == null) {
            // Single-line: vertically centered
            if (!titleLines.isEmpty()) {
                g.text(font, titleLines.getFirst(), textX, (this.height() - font.lineHeight) / 2, 0xFFFFFFFF);
            }
        } else {
            // Two lines: title + description
            int y = PADDING_TOP;
            if (!titleLines.isEmpty()) {
                g.text(font, titleLines.getFirst(), textX, y, 0xFFFFFFFF);
            }
            y += LINE_SPACING;
            List<FormattedCharSequence> descLines = font.split(description, maxTextWidth);
            if (!descLines.isEmpty()) {
                g.text(font, descLines.getFirst(), textX, y, 0xFFAAAAAA);
            }
        }

        // Calculate visibility based on display time
        if (this.firstDrawTime == 0L) {
            this.firstDrawTime = time;
        }
        double multiplier = toastComponent.getNotificationDisplayTimeMultiplier();
        long elapsed = time - this.firstDrawTime;
        if (elapsed >= (long) (DISPLAY_TIME_MS * multiplier)) {
            return Toast.Visibility.HIDE;
        }
        return Toast.Visibility.SHOW;
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
        mc.getToasts().addToast(toast);
    }
}
