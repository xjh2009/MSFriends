package dev.msf.friends.screen;

import net.minecraftforge.fml.client.config.GuiButtonExt;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple button wrapper for 1.13.2 Forge that adds a {@link Runnable} callback.
 * <p>1.13.2 GuiButtonExt has no IPressable; we override onClick() directly.
 * <p>GuiButtonExt(int id, int x, int y, int width, int height, String text).
 */
public class SimpleButton extends GuiButtonExt {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(10000);
    private final Runnable action;

    public SimpleButton(int x, int y, int width, int height, String message, Runnable action) {
        super(ID_COUNTER.getAndIncrement(), x, y, width, height, message);
        this.action = action;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.action.run();
    }
}
