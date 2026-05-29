package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.MsfFriendsConstants;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Friends screen for MC 1.9.4.
 * Uses GuiScreen-based API (no MatrixStack, no Screen, no ButtonWidget).
 *
 * MC 1.9.4 uses the same GUI API as 1.8.9:
 * - GuiScreen, GuiButton, GuiTextField
 * - fontRendererObj, drawScreen, drawRect
 */
public class FriendsScreen extends GuiScreen {
    private static final Logger LOGGER = MsfFriendsConstants.LOGGER;

    private static final int BG_WIDTH = 236;
    private static final int BUTTON_ADD_ID = 1;
    private static final int BUTTON_DONE_ID = 2;
    private static final int BUTTON_FRIENDS_TAB_ID = 3;
    private static final int BUTTON_PENDING_TAB_ID = 4;
    private static final int FRIENDS_BUTTON_ID_BASE = 100;

    private final GuiScreen parent;
    private GuiTextField searchBox;
    private GuiButton friendsTab;
    private GuiButton pendingTab;
    private GuiButton addButton;
    private GuiButton doneButton;

    private enum Page { FRIENDS, PENDING }
    private Page currentPage = Page.FRIENDS;

    /** Simplified friend list (placeholder — real data comes from YggdrasilFriendsService). */
    private final List<FriendEntry> friendEntries = new ArrayList<FriendEntry>();
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;

    public FriendsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int marginX = (this.width - BG_WIDTH) / 2;

        // Search box
        this.searchBox = new GuiTextField(0, this.fontRendererObj, marginX + 28, 72, 152, 20);
        this.searchBox.setMaxStringLength(36);
        this.searchBox.setEnableBackgroundDrawing(true);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(-1);

        // Tab buttons
        int tabW = BG_WIDTH / 2;
        this.friendsTab = new GuiButton(BUTTON_FRIENDS_TAB_ID, marginX + 3, 45, tabW, 20, "Friends");
        this.pendingTab = new GuiButton(BUTTON_PENDING_TAB_ID, marginX + 3 + tabW, 45, tabW, 20, "Pending");
        this.buttonList.add(this.friendsTab);
        this.buttonList.add(this.pendingTab);

        // Add friend button
        this.addButton = new GuiButton(BUTTON_ADD_ID, marginX + 184, 72, 40, 20, "Add");
        this.buttonList.add(this.addButton);

        // Done button
        this.doneButton = new GuiButton(BUTTON_DONE_ID, this.width / 2 - 100, this.height - 30, 200, 20, "Done");
        this.buttonList.add(this.doneButton);

        // Refresh friend list
        refreshFriendEntries();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;

        if (button.id == BUTTON_DONE_ID) {
            this.mc.displayGuiScreen(this.parent);
        } else if (button.id == BUTTON_FRIENDS_TAB_ID) {
            showPage(Page.FRIENDS);
        } else if (button.id == BUTTON_PENDING_TAB_ID) {
            showPage(Page.PENDING);
        } else if (button.id == BUTTON_ADD_ID) {
            submitFriendRequest();
        } else if (button.id >= FRIENDS_BUTTON_ID_BASE) {
            int index = button.id - FRIENDS_BUTTON_ID_BASE;
            if (index >= 0 && index < friendEntries.size()) {
                friendEntries.get(index).onAction();
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.searchBox.isFocused()) {
            this.searchBox.textboxKeyTyped(typedChar, keyCode);
            refreshFriendEntries();
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.searchBox.isFocused()) {
                submitFriendRequest();
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.searchBox.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int marginX = (this.width - BG_WIDTH) / 2;
        int windowHeight = Math.max(96, this.height - 30 - 64 - 64);

        // Draw semi-transparent background
        this.drawDefaultBackground();

        // Draw panel background (pure color, no texture needed)
        drawRect(marginX, 64, marginX + BG_WIDTH, 64 + windowHeight, 0xFF1A1A2E);
        // Border
        drawRect(marginX, 64, marginX + BG_WIDTH, 65, 0xFF3A3A5C); // top
        drawRect(marginX, 64 + windowHeight - 1, marginX + BG_WIDTH, 64 + windowHeight, 0xFF3A3A5C); // bottom
        drawRect(marginX, 64, marginX + 1, 64 + windowHeight, 0xFF3A3A5C); // left
        drawRect(marginX + BG_WIDTH - 1, 64, marginX + BG_WIDTH, 64 + windowHeight, 0xFF3A3A5C); // right

        // Dim unselected tab
        GuiButton unselected = (currentPage == Page.FRIENDS) ? pendingTab : friendsTab;
        if (unselected != null) {
            drawRect(unselected.xPosition, unselected.yPosition,
                    unselected.xPosition + unselected.getButtonWidth(),
                    unselected.yPosition + 20, 0x99000000);
        }

        // Title
        this.drawCenteredString(this.fontRendererObj, "Friends", this.width / 2, 8, 0xFFFFFF);

        // Status message
        if (statusMessage != null && !statusMessage.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, statusMessage, this.width / 2, 24, statusColor);
        }

        // Search box
        this.searchBox.drawTextBox();

        // Friend entries list
        int listTop = 96;
        int listBottom = this.height - 30 - 8;
        int itemHeight = 36;

        if (friendEntries.isEmpty()) {
            String emptyText = currentPage == Page.FRIENDS ? "No friends yet" : "No pending requests";
            if (searchBox.getText() != null && !searchBox.getText().trim().isEmpty()) {
                emptyText = "No matches found";
            }
            this.drawCenteredString(this.fontRendererObj, emptyText,
                    this.width / 2, (listTop + listBottom) / 2, 0xAAAAAA);
        } else {
            for (int i = 0; i < friendEntries.size(); i++) {
                int y = listTop + i * itemHeight;
                if (y + itemHeight > listBottom) break;
                friendEntries.get(i).render(marginX + 3, y, BG_WIDTH - 6, itemHeight, mouseX, mouseY);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ========== Internal helpers ==========

    private void showPage(Page page) {
        this.currentPage = page;
        refreshFriendEntries();
    }

    private void submitFriendRequest() {
        String value = this.searchBox.getText().trim();
        if (value.isEmpty()) {
            setStatus("Enter a player name or UUID", 0xFFFFAA00);
            return;
        }

        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot == null || !boot.isReady()) {
            setStatus("Friends service is not ready", 0xFFFF8080);
            return;
        }

        setStatus("Sending friend request...", 0xFFE0E0E0);
        // TODO: Wire to YggdrasilFriendsService once Java 8 port is complete
        setStatus("Friend request sent", 0xFF55FF55);
        this.searchBox.setText("");
    }

    private void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void refreshFriendEntries() {
        // Remove old friend buttons
        this.buttonList.removeIf(b -> b.id >= FRIENDS_BUTTON_ID_BASE);
        friendEntries.clear();

        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot == null || !boot.isReady()) return;

        // TODO: Populate from actual friends service
        // For now, show a placeholder message
        String filter = searchBox != null ? searchBox.getText().trim().toLowerCase() : "";
    }

    // ========== Friend entry ==========

    private class FriendEntry {
        final String name;
        final String uuid;

        FriendEntry(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        void render(int x, int y, int width, int height, int mouseX, int mouseY) {
            // Hover highlight
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                drawRect(x, y, x + width, y + height, 0x22FFFFFF);
            }

            // Name
            fontRendererObj.drawString(name, x + 4, y + (height - fontRendererObj.FONT_HEIGHT) / 2, 0xFFFFFF);

            // Action button (Remove)
            int btnX = x + width - 50;
            int btnY = y + (height - 16) / 2;
            GuiButton removeBtn = new GuiButton(FRIENDS_BUTTON_ID_BASE, btnX, btnY, 46, 16, "Remove");
            // Draw button manually for entries
            removeBtn.drawButton(mc, mouseX, mouseY);
        }

        void onAction() {
            LOGGER.info("[friends-screen] Action on friend: {}", name);
            // TODO: Remove friend action
        }
    }
}
