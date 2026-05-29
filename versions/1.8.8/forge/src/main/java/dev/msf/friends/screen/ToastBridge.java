package dev.msf.friends.screen;

import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Toast notification system for MC 1.8.8.
 * MC 1.8.8 has no built-in toast system, so we render our own overlay.
 */
public final class ToastBridge {
    private static final Logger LOGGER = Logging.get();
    private static final int MAX_TOASTS = 4;
    private static final long TOAST_DURATION_MS = 5000;
    private static final int TOAST_WIDTH = 160;
    private static final int TOAST_HEIGHT = 32;

    private static final Deque<ToastEntry> toasts = new ArrayDeque<>();

    public static void showToast(String type, String name, @Nullable UUID profileId) {
        String title = getTitleForType(type);
        String description = getDescriptionForType(type, name);
        synchronized (toasts) {
            if (toasts.size() >= MAX_TOASTS) {
                toasts.pollFirst();
            }
            toasts.addLast(new ToastEntry(title, description, System.currentTimeMillis()));
        }
    }

    /**
     * Call from a render event (e.g. RenderGameOverlayEvent.Post) to draw active toasts.
     */
    public static void renderToasts(Minecraft mc, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        synchronized (toasts) {
            // Remove expired toasts
            while (!toasts.isEmpty() && (now - toasts.peekFirst().createTime) > TOAST_DURATION_MS) {
                toasts.pollFirst();
            }
            if (toasts.isEmpty()) return;

            FontRenderer fr = mc.fontRendererObj;
            int y = 10;
            for (ToastEntry toast : toasts) {
                int x = screenWidth - TOAST_WIDTH - 10;
                // Background
                Gui.drawRect(x, y, x + TOAST_WIDTH, y + TOAST_HEIGHT, 0xCC000000);
                // Border
                Gui.drawRect(x, y, x + TOAST_WIDTH, y + 1, 0xFF555555);
                Gui.drawRect(x, y + TOAST_HEIGHT - 1, x + TOAST_WIDTH, y + TOAST_HEIGHT, 0xFF555555);
                Gui.drawRect(x, y, x + 1, y + TOAST_HEIGHT, 0xFF555555);
                Gui.drawRect(x + TOAST_WIDTH - 1, y, x + TOAST_WIDTH, y + TOAST_HEIGHT, 0xFF555555);
                // Title
                fr.drawStringWithShadow(toast.title, x + 6, y + 4, 0xFFFFFF);
                // Description
                fr.drawStringWithShadow(toast.description, x + 6, y + 16, 0xAAAAAA);
                y += TOAST_HEIGHT + 4;
            }
        }
    }

    private static String getTitleForType(String type) {
        switch (type) {
            case "friend.added": return "Friend Added";
            case "friend.status.online": return "Friend Online";
            case "friend.status.offline": return "Friend Offline";
            default: return "MSF Friends";
        }
    }

    private static String getDescriptionForType(String type, String name) {
        switch (type) {
            case "friend.added": return name + " is now your friend";
            case "friend.status.online": return name + " is now online";
            case "friend.status.offline": return name + " is now offline";
            default: return name;
        }
    }

    private static class ToastEntry {
        final String title;
        final String description;
        final long createTime;
        ToastEntry(String title, String description, long createTime) {
            this.title = title;
            this.description = description;
            this.createTime = createTime;
        }
    }

    private ToastBridge() {}
}
