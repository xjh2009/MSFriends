package dev.msf.friends.screen;

import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.Button.IPressable;

/**
 * Simple button wrapper for 1.13.2 Forge that adds a {@link Runnable} callback.
 * <p>1.13.2 Button has no IPressable; we override onClick() directly.
 * <p>Button(int id, int x, int y, int width, int height, String text).
 */
public class SimpleButton extends Button {

    private final Runnable action;

    public SimpleButton(int x, int y, int width, int height, String message, Runnable action) {
        super(x, y, width, height, message, b -> {});
        this.action = action;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.action.run();
    }
}
