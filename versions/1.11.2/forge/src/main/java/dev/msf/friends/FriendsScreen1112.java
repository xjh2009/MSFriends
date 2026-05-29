package dev.msf.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Friends screen for MC 1.11.2 using MCP-mapped GuiScreen API.
 * Adapted from the 1.19.2 Yarn-based FriendsScreen.
 */
public class FriendsScreen1112 extends GuiScreen {

    private static final Logger LOGGER = Logging1112.get();

    private static final int BTN_BACK = 0;
    private static final int BTN_ADD_FRIEND = 1;
    private static final int BTN_ONLINE_TAB = 2;
    private static final int BTN_ALL_TAB = 3;
    private static final int BTN_SEARCH = 4;

    private final GuiScreen parent;
    private GuiTextField searchField;
    private FriendList list;

    /** Simplified friend entry. */
    public static class FriendEntry {
        public final UUID uuid;
        public final String name;
        public final boolean online;
        public FriendEntry(UUID uuid, String name, boolean online) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
        }
    }

    private final List<FriendEntry> allFriends = new ArrayList<FriendEntry>();
    private final List<FriendEntry> displayedFriends = new ArrayList<FriendEntry>();
    private boolean showOnlineOnly = false;

    public FriendsScreen1112(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        super.initGui();
        int cx = this.width / 2;

        this.buttonList.add(new GuiButton(BTN_BACK, cx - 100, this.height - 30, 200, 20, "Back"));
        this.buttonList.add(new GuiButton(BTN_ADD_FRIEND, cx + 110, 6, 80, 20, "Add Friend"));
        this.buttonList.add(new GuiButton(BTN_ONLINE_TAB, cx - 155, 30, 75, 20, "Online"));
        this.buttonList.add(new GuiButton(BTN_ALL_TAB, cx - 75, 30, 75, 20, "All"));

        this.searchField = new GuiTextField(0, this.fontRenderer, cx + 80, 32, 110, 16);
        this.searchField.setMaxStringLength(50);
        this.searchField.setEnableBackgroundDrawing(false);

        this.list = new FriendList(this.mc, this.width, this.height, 55, this.height - 40, 24);
        refreshDisplayedFriends();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BTN_BACK:
                this.mc.displayGuiScreen(this.parent);
                break;
            case BTN_ADD_FRIEND:
                // TODO: open add-friend dialog
                LOGGER.info("[gui] Add Friend clicked");
                break;
            case BTN_ONLINE_TAB:
                showOnlineOnly = true;
                refreshDisplayedFriends();
                break;
            case BTN_ALL_TAB:
                showOnlineOnly = false;
                refreshDisplayedFriends();
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.searchField.isFocused()) {
            this.searchField.textboxKeyTyped(typedChar, keyCode);
            refreshDisplayedFriends();
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int cx = this.width / 2;

        // Title
        this.drawCenteredString(this.fontRenderer, "Friends", cx, 12, 0xFFFFFF);

        // Search field
        this.searchField.drawTextBox();

        // Friend list
        if (this.list != null) {
            this.list.drawScreen(mouseX, mouseY, partialTicks);
        }

        // Status bar
        int online = 0;
        for (FriendEntry e : allFriends) {
            if (e.online) online++;
        }
        String status = online + " online / " + allFriends.size() + " friends";
        this.drawCenteredString(this.fontRenderer, status, cx, this.height - 38, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void refreshDisplayedFriends() {
        displayedFriends.clear();
        String filter = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        for (FriendEntry f : allFriends) {
            if (showOnlineOnly && !f.online) continue;
            if (!filter.isEmpty() && !f.name.toLowerCase().contains(filter)) continue;
            displayedFriends.add(f);
        }
        if (this.list != null) {
            this.list.setEntries(displayedFriends);
        }
    }

    /** Set the friend list data (called from boot). */
    public void setFriends(List<FriendEntry> friends) {
        allFriends.clear();
        allFriends.addAll(friends);
        refreshDisplayedFriends();
    }

    // ========== Inner list class ==========

    private class FriendList {
        private final int width, height, top, bottom, slotHeight;
        private final net.minecraft.client.Minecraft mc;
        private final List<FriendEntry> entries = new ArrayList<FriendEntry>();
        private int scrollY;
        private int selected = -1;

        FriendList(net.minecraft.client.Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
            this.mc = mc;
            this.width = width;
            this.height = height;
            this.top = top;
            this.bottom = bottom;
            this.slotHeight = slotHeight;
        }

        void setEntries(List<FriendEntry> list) {
            this.entries.clear();
            this.entries.addAll(list);
        }

        void drawScreen(int mouseX, int mouseY, float partialTicks) {
            int cx = width / 2;
            int listW = 300;
            int left = cx - listW / 2;
            int right = cx + listW / 2;

            GlStateManager.disableTexture2D();
            // Separator line
            GuiScreen.drawRect(left, top, right, top + 1, 0xFF555555);
            GlStateManager.enableTexture2D();

            int maxVisible = (bottom - top) / slotHeight;
            for (int i = 0; i < maxVisible && i < entries.size(); i++) {
                FriendEntry entry = entries.get(i);
                int y = top + 2 + i * slotHeight;

                // Selection highlight
                if (i == selected) {
                    GuiScreen.drawRect(left, y - 1, right, y + slotHeight - 2, 0x800080FF);
                }

                // Online indicator
                int dotColor = entry.online ? 0xFF00FF00 : 0xFF888888;
                GuiScreen.drawRect(left + 4, y + 4, left + 10, y + 10, dotColor);

                // Name
                mc.fontRenderer.drawString(entry.name, left + 16, y + 3, 0xFFFFFF);

                // UUID (dimmed)
                String shortUuid = entry.uuid.toString().substring(0, 8) + "...";
                mc.fontRenderer.drawString(shortUuid, left + 16, y + 14, 0x666666);

                // Online/offline label
                String status = entry.online ? "\u00a7aOnline" : "\u00a77Offline";
                mc.fontRenderer.drawString(status, right - 50, y + 3, 0xFFFFFF);
            }
        }

        /** Returns the clicked entry, or null. */
        FriendEntry getEntryAt(int mouseX, int mouseY) {
            int cx = width / 2;
            int listW = 300;
            int left = cx - listW / 2;
            int right = cx + listW / 2;
            if (mouseX < left || mouseX > right || mouseY < top || mouseY > bottom) return null;
            int idx = (mouseY - top - 2) / slotHeight;
            if (idx >= 0 && idx < entries.size()) {
                selected = idx;
                return entries.get(idx);
            }
            return null;
        }
    }
}
