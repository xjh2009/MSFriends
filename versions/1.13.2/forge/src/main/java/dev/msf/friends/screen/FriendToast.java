package dev.msf.friends.screen;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Custom toast for MC 1.13.2 MCP that displays a player's skin head alongside
 * the notification text.
 *
     * <p>1.13.2 MCP Toast API: single {@code draw(GuiToast, long)} method returning Visibility.
 */
public class FriendToast implements IToast {

    private static final ResourceLocation BACKGROUND_TEXTURE =
            new ResourceLocation("textures/gui/toasts.png");

    private static final int TOAST_WIDTH = 160;
    private static final int TOAST_HEIGHT = 32;
    private static final int FACE_SIZE = 20;
    private static final int PADDING_LEFT = 8;
    private static final int FACE_TEXT_GAP = 6;
    private static final int PADDING_TOP = 7;
    private static final int LINE_SPACING = 12;
    private static final int TEXT_LEFT = PADDING_LEFT + FACE_SIZE + FACE_TEXT_GAP;
    private static final long DISPLAY_TIME_MS = 5000L;

    private final ITextComponent title;
    private final @Nullable ITextComponent description;
    private final @Nullable UUID profileId;

    private volatile @Nullable ResourceLocation resolvedSkin;
    private boolean skinRequested;
    private long firstDrawTime;

    public FriendToast(ITextComponent title, @Nullable ITextComponent description, @Nullable UUID profileId) {
        this.title = title;
        this.description = description;
        this.profileId = profileId;
    }

    @Override
    public Visibility draw(GuiToast toastGui, long startTime) {
        if (this.firstDrawTime == 0L) {
            this.firstDrawTime = startTime;
        }

        Minecraft mc = toastGui.getMinecraft();

        if (!skinRequested && profileId != null) {
            skinRequested = true;
            requestSkin(mc, profileId);
        }

        // Background
        mc.getTextureManager().bindTexture(BACKGROUND_TEXTURE);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawScaledCustomSizeModalRect(0, 0, 0, 0, TOAST_WIDTH, TOAST_HEIGHT, TOAST_WIDTH, TOAST_HEIGHT, 256, 256);

        // Face
        int faceX = PADDING_LEFT;
        int faceY = (TOAST_HEIGHT - FACE_SIZE) / 2;
        ResourceLocation skinLoc = this.resolvedSkin;
        if (skinLoc == null && profileId != null) {
            skinLoc = DefaultPlayerSkin.getDefaultSkin(profileId);
        }
        if (skinLoc == null) {
            skinLoc = DefaultPlayerSkin.getDefaultSkin(UUID.randomUUID());
        }
        renderFace(mc, skinLoc, faceX, faceY);

        // Text
        int textX = TEXT_LEFT;
        int titleY = PADDING_TOP;
        FontRenderer fontRenderer = mc.fontRenderer;
        fontRenderer.drawString(title.getFormattedText(), textX, titleY, 0xFFFFFFFF);

        if (description != null) {
            fontRenderer.drawString(description.getFormattedText(), textX, titleY + LINE_SPACING, 0xFFAAAAAA);
        }

        long elapsed = startTime - this.firstDrawTime;
        if (elapsed >= DISPLAY_TIME_MS) {
            return Visibility.HIDE;
        }
        return Visibility.SHOW;
    }

    private void renderFace(Minecraft mc, ResourceLocation skinLoc, int x, int y) {
        mc.getTextureManager().bindTexture(skinLoc);
        GlStateManager.color4f(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        int s = FACE_SIZE;
        // Base head layer
        Gui.drawScaledCustomSizeModalRect(x, y, 8f, 8f, 8, 8, s, s, 64, 64);
        // Overlay head layer
        Gui.drawScaledCustomSizeModalRect(x, y, 40f, 8f, 8, 8, s, s, 64, 64);
        GlStateManager.disableBlend();
    }

    private void requestSkin(Minecraft mc, UUID id) {
        PlayerSkinResolver.fetchSkin(mc, id, "")
                .thenAccept(resolved -> this.resolvedSkin = resolved);
    }

    public static void show(ITextComponent title, ITextComponent description, @Nullable UUID profileId) {
        Minecraft.getInstance().getToastGui().add(new FriendToast(title, description, profileId));
    }
}
