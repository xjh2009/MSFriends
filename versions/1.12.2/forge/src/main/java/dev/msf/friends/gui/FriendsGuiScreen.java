package dev.msf.friends.gui;

import dev.msf.friends.MsfFriendsBoot1122;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Main friends screen for MC 1.12.2. Uses GuiScreen API (no MatrixStack).
 * Shows friend list with search, friend requests, and P2P join.
 */
public class FriendsGuiScreen extends GuiScreen {
    private static final Logger LOGGER = Logging.get();

    private GuiTextField searchField;
    private GuiScreen parent;

    private static final int BACK_BUTTON_ID = 0;
    private static final int ADD_FRIEND_BUTTON_ID = 1;
    private static final int REMOVE_FRIEND_BUTTON_ID = 2;
    private static final int JOIN_BUTTON_ID = 3;
    private static final int INVITE_BUTTON_ID = 4;
    private static final int ACCEPT_REQUEST_BUTTON_ID = 5;
    private static final int REJECT_REQUEST_BUTTON_ID = 6;

    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;

    public FriendsGuiScreen() {
        this.parent = null;
    }

    public FriendsGuiScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        // Search field
        searchField = new GuiTextField(0, fontRenderer, width / 2 - 100, 22, 200, 20);
        searchField.setMaxStringLength(32);
        searchField.setFocused(false);
        searchField.setText("");

        // Buttons
        buttonList.clear();
        buttonList.add(new GuiButton(BACK_BUTTON_ID, width / 2 - 100, height - 28, 200, 20, "Back"));
        buttonList.add(new GuiButton(ADD_FRIEND_BUTTON_ID, 4, 4, 20, 20, "+"));
        buttonList.add(new GuiButton(JOIN_BUTTON_ID, width - 24, height - 52, 20, 20, "J"));
        buttonList.add(new GuiButton(INVITE_BUTTON_ID, width - 48, height - 52, 20, 20, "I"));
        buttonList.add(new GuiButton(REMOVE_FRIEND_BUTTON_ID, width - 72, height - 52, 20, 20, "X"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BACK_BUTTON_ID:
                mc.displayGuiScreen(parent);
                break;
            case ADD_FRIEND_BUTTON_ID:
                // Open add friend dialog - use search field text as friend name
                String friendName = searchField.getText().trim();
                if (!friendName.isEmpty()) {
                    addFriend(friendName);
                }
                break;
            case JOIN_BUTTON_ID:
                // Join selected friend's game
                joinSelectedFriend();
                break;
            case INVITE_BUTTON_ID:
                // Invite selected friend
                inviteSelectedFriend();
                break;
            case REMOVE_FRIEND_BUTTON_ID:
                // Remove selected friend
                removeSelectedFriend();
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
            // Search field handled the input
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Background
        drawDefaultBackground();

        // Title
        drawCenteredString(fontRenderer, "Friends", width / 2, 8, 0xFFFFFF);

        // Search field
        if (searchField != null) {
            searchField.drawTextBox();
        }

        // Status message
        if (!statusMessage.isEmpty()) {
            drawCenteredString(fontRenderer, statusMessage, width / 2, height - 48, statusColor);
        }

        // Friend list placeholder
        drawFriendList(mouseX, mouseY);

        // Buttons
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawFriendList(int mouseX, int mouseY) {
        MsfFriendsBoot1122 boot = MsfFriendsBoot1122.get();
        if (boot == null || boot.social() == null) {
            drawCenteredString(fontRenderer, "Loading...", width / 2, height / 2, 0xAAAAAA);
            return;
        }

        // Draw a simple list area
        int listX = width / 2 - 100;
        int listY = 48;
        int listW = 200;
        int listH = height - 100;

        drawRect(listX, listY, listX + listW, listY + listH, 0x80000000);

        // Show friend count
        int friendCount = boot.social().getFriendCount();
        drawCenteredString(fontRenderer, "Friends: " + friendCount, width / 2, listY + 8, 0x55FF55);

        // Show friends
        java.util.List<String> friendNames = boot.social().getFriendNames();
        int y = listY + 24;
        for (String name : friendNames) {
            if (y > listY + listH - 12) break;

            boolean selected = isMouseOverEntry(mouseX, mouseY, listX, y, listW, 12);
            if (selected) {
                drawRect(listX, y, listX + listW, y + 12, 0x40FFFFFF);
            }

            fontRenderer.drawStringWithShadow(name, listX + 4, y + 2, 0xAAAAAA);
            y += 14;
        }

        if (friendCount == 0) {
            drawCenteredString(fontRenderer, "No friends yet", width / 2, height / 2, 0xAAAAAA);
            drawCenteredString(fontRenderer, "Type a name and press + to add", width / 2, height / 2 + 14, 0x777777);
        }
    }

    private boolean isMouseOverEntry(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void addFriend(String name) {
        MsfFriendsBoot1122 boot = MsfFriendsBoot1122.get();
        if (boot == null || boot.social() == null) {
            statusMessage = "System not ready";
            statusColor = 0xFF5555;
            return;
        }
        // Use async friend request
        boot.social().sendFriendRequest(name).thenAccept(success -> {
            mc.addScheduledTask(() -> {
                if (success) {
                    statusMessage = "Friend request sent to " + name;
                    statusColor = 0x55FF55;
                    searchField.setText("");
                } else {
                    statusMessage = "Failed to send request";
                    statusColor = 0xFF5555;
                }
            });
        });
    }

    private void joinSelectedFriend() {
        // TODO: implement friend selection and join
        statusMessage = "Select a friend to join";
        statusColor = 0xFFFF55;
    }

    private void inviteSelectedFriend() {
        // TODO: implement friend selection and invite
        statusMessage = "Select a friend to invite";
        statusColor = 0xFFFF55;
    }

    private void removeSelectedFriend() {
        // TODO: implement friend selection and remove
        statusMessage = "Select a friend to remove";
        statusColor = 0xFFFF55;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
