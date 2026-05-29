package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.RemoteFriendListUpdateHandler;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Friends screen for MC 1.10.2 with real friend list functionality.
 */
public class FriendsScreen extends GuiScreen {

    private static final Logger LOGGER = Logging.get();

    private static final int BUTTON_DONE = 0;
    private static final int BUTTON_ADD_FRIEND = 1;
    private static final int BUTTON_REFRESH = 2;
    private static final int BUTTON_TOGGLE_HIDDEN = 3;

    private static final int BUTTON_ACCEPT_START = 100;
    private static final int BUTTON_DECLINE_START = 200;
    private static final int BUTTON_REMOVE_START = 300;

    private final GuiScreen parent;
    private GuiTextField searchBox;
    private GuiTextField addFriendBox;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;

    private int scrollOffset = 0;
    private final int ROW_HEIGHT = 14;
    private final int LIST_Y_START = 80;
    private final int LIST_HEIGHT = 120;

    // Tab state: 0=friends, 1=incoming, 2=outgoing
    private int activeTab = 0;
    private Runnable updateListener;
    private Runnable presenceListener;

    public FriendsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();

        // Search box
        searchBox = new GuiTextField(1, fontRendererObj, width / 2 - 100, 40, 150, 16);
        searchBox.setMaxStringLength(36);
        searchBox.setFocused(false);

        // Add friend input
        addFriendBox = new GuiTextField(2, fontRendererObj, width / 2 - 100, 60, 150, 16);
        addFriendBox.setMaxStringLength(36);
        addFriendBox.setFocused(false);

        // Buttons
        buttonList.add(new GuiButton(BUTTON_DONE, width / 2 - 100, height - 26, 200, 20, "Done"));
        buttonList.add(new GuiButton(BUTTON_ADD_FRIEND, width / 2 + 55, 58, 45, 20, "Add"));
        buttonList.add(new GuiButton(BUTTON_REFRESH, width / 2 + 55, 38, 45, 20, "Refresh"));
        buttonList.add(new GuiButton(BUTTON_TOGGLE_HIDDEN, width / 2 - 100, height - 50, 100, 20, "Appear Offline"));

        // Register listeners for automatic UI refresh
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot != null && boot.socialManager() != null) {
            PlayerSocialManager social = boot.socialManager();
            final Minecraft mcInst = Minecraft.getMinecraft();
            updateListener = new Runnable() {
                @Override public void run() {
                    mcInst.addScheduledTask(new Runnable() {
                        @Override public void run() { /* drawScreen will pick up latest data */ }
                    });
                }
            };
            presenceListener = new Runnable() {
                @Override public void run() {
                    mcInst.addScheduledTask(new Runnable() {
                        @Override public void run() { /* drawScreen will pick up latest data */ }
                    });
                }
            };
            social.addFriendListUpdateListener(updateListener);
            social.getPresenceHandler().addPresenceListener(presenceListener);
            // Trigger initial data load
            social.getRemoteFriendListUpdateHandler().forceUpdate();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot == null || boot.socialManager() == null) return;
        PlayerSocialManager social = boot.socialManager();

        if (button.id == BUTTON_DONE) {
            mc.displayGuiScreen(parent);
        } else if (button.id == BUTTON_ADD_FRIEND) {
            String name = addFriendBox.getText().trim();
            if (!name.isEmpty()) {
                social.sendFriendRequest(name).whenComplete((result, err) -> {
                    bridge().executeOnClientThread(() -> {
                        if (err != null) {
                            statusMessage = "Error: " + err.getMessage();
                            statusColor = 0xFF5555;
                        } else {
                            statusMessage = "Friend request sent to " + name;
                            statusColor = 0x55FF55;
                            addFriendBox.setText("");
                        }
                    });
                });
                statusMessage = "Sending request...";
                statusColor = 0xFFFF55;
            }
        } else if (button.id == BUTTON_REFRESH) {
            social.getRemoteFriendListUpdateHandler().forceUpdate();
            statusMessage = "Refreshing...";
            statusColor = 0xFFFF55;
        } else if (button.id == BUTTON_TOGGLE_HIDDEN) {
            boolean newHidden = !bridge().hiddenMode();
            social.getPresenceHandler().setHiddenMode(newHidden);
            statusMessage = newHidden ? "Now appearing offline" : "Now appearing online";
            statusColor = 0x55FF55;
        }

        // Handle accept/decline/remove buttons
        List<PlayerSocialManager.PlayerData> incoming = social.getIncomingRequests();
        for (int i = 0; i < incoming.size(); i++) {
            if (button.id == BUTTON_ACCEPT_START + i) {
                social.acceptIncomingFriendRequest(incoming.get(i).id());
                statusMessage = "Accepted " + incoming.get(i).name();
                statusColor = 0x55FF55;
                return;
            }
            if (button.id == BUTTON_DECLINE_START + i) {
                social.declineIncomingFriendRequest(incoming.get(i).id());
                statusMessage = "Declined " + incoming.get(i).name();
                statusColor = 0xFFFF55;
                return;
            }
        }

        List<PlayerSocialManager.PlayerData> friends = getFilteredFriends();
        for (int i = 0; i < friends.size(); i++) {
            if (button.id == BUTTON_REMOVE_START + i) {
                social.removeFriend(friends.get(i).id());
                statusMessage = "Removed " + friends.get(i).name();
                statusColor = 0xFFFF55;
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.textboxKeyTyped(typedChar, keyCode);
        } else if (addFriendBox != null && addFriendBox.isFocused()) {
            addFriendBox.textboxKeyTyped(typedChar, keyCode);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (searchBox != null) searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        if (addFriendBox != null) addFriendBox.mouseClicked(mouseX, mouseY, mouseButton);

        // Tab clicks
        int tabY = LIST_Y_START - 16;
        if (mouseY >= tabY && mouseY < tabY + 12) {
            int tabX = width / 2 - 150;
            int tabW = 100;
            if (mouseX >= tabX && mouseX < tabX + tabW) {
                activeTab = 0; scrollOffset = 0;
            } else if (mouseX >= tabX + tabW && mouseX < tabX + tabW * 2) {
                activeTab = 1; scrollOffset = 0;
            } else if (mouseX >= tabX + tabW * 2 && mouseX < tabX + tabW * 3) {
                activeTab = 2; scrollOffset = 0;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Title
        drawCenteredString(fontRendererObj, "MSF Friends", width / 2, 8, 0xFFFFFF);

        // Status message
        if (statusMessage != null && !statusMessage.isEmpty()) {
            drawCenteredString(fontRendererObj, statusMessage, width / 2, 22, statusColor);
        }

        // Hidden mode indicator
        if (bridge() != null && bridge().hiddenMode()) {
            drawCenteredString(fontRendererObj, "\u00a7c(Appearing Offline)", width / 2 + 80, 8, 0xFF5555);
        }

        // Draw input boxes
        if (searchBox != null) searchBox.drawTextBox();
        if (addFriendBox != null) addFriendBox.drawTextBox();

        // Tabs
        drawTabs(mouseX, mouseY);

        // Draw the list based on active tab
        if (activeTab == 0) {
            drawFriendsList(mouseX, mouseY);
        } else if (activeTab == 1) {
            drawIncomingRequests(mouseX, mouseY);
        } else if (activeTab == 2) {
            drawOutgoingRequests();
        }

        // Friend list state
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot != null && boot.socialManager() != null) {
            RemoteFriendListUpdateHandler.State state = boot.socialManager().getFriendListState();
            if (state != RemoteFriendListUpdateHandler.State.SUCCESS) {
                String stateStr = "Status: " + state.name();
                drawCenteredString(fontRendererObj, stateStr, width / 2, height - 62, 0xFFAA00);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTabs(int mouseX, int mouseY) {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot == null || boot.socialManager() == null) return;
        PlayerSocialManager social = boot.socialManager();

        int tabX = width / 2 - 150;
        int tabY = LIST_Y_START - 16;
        int tabW = 100;

        String[] tabNames = {
            "Friends (" + social.getFriends().size() + ")",
            "Incoming (" + social.getIncomingRequests().size() + ")",
            "Outgoing (" + social.getOutgoingRequests().size() + ")"
        };

        for (int i = 0; i < 3; i++) {
            int color = (i == activeTab) ? 0x55FF55 : 0x808080;
            drawString(fontRendererObj, tabNames[i], tabX + i * tabW + 4, tabY, color);
        }
    }

    private void drawFriendsList(int mouseX, int mouseY) {
        List<PlayerSocialManager.PlayerData> friends = getFilteredFriends();
        if (friends.isEmpty()) {
            drawCenteredString(fontRendererObj, "No friends found", width / 2, LIST_Y_START + 20, 0x808080);
            return;
        }

        int y = LIST_Y_START;
        int visibleRows = LIST_HEIGHT / ROW_HEIGHT;
        int start = Math.min(scrollOffset, Math.max(0, friends.size() - visibleRows));

        for (int i = start; i < friends.size() && i < start + visibleRows; i++) {
            PlayerSocialManager.PlayerData friend = friends.get(i);
            int rowY = y + (i - start) * ROW_HEIGHT;

            // Highlight on hover
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= width / 2 - 150 && mouseX < width / 2 + 100) {
                drawRect(width / 2 - 150, rowY, width / 2 + 100, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }

            drawString(fontRendererObj, friend.name(), width / 2 - 146, rowY + 2, 0xCCCCCC);
        }

        // Scrollbar
        if (friends.size() > visibleRows) {
            int scrollbarX = width / 2 + 105;
            int scrollbarHeight = LIST_HEIGHT;
            int thumbHeight = Math.max(10, scrollbarHeight * visibleRows / friends.size());
            int thumbY = LIST_Y_START + (scrollbarHeight - thumbHeight) * start / Math.max(1, friends.size() - visibleRows);
            drawRect(scrollbarX, LIST_Y_START, scrollbarX + 4, LIST_Y_START + scrollbarHeight, 0xFF333333);
            drawRect(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF888888);
        }
    }

    private void drawIncomingRequests(int mouseX, int mouseY) {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot == null || boot.socialManager() == null) return;
        List<PlayerSocialManager.PlayerData> incoming = boot.socialManager().getIncomingRequests();

        if (incoming.isEmpty()) {
            drawCenteredString(fontRendererObj, "No incoming requests", width / 2, LIST_Y_START + 20, 0x808080);
            return;
        }

        int y = LIST_Y_START;
        int visibleRows = LIST_HEIGHT / ROW_HEIGHT;
        int start = Math.min(scrollOffset, Math.max(0, incoming.size() - visibleRows));

        for (int i = start; i < incoming.size() && i < start + visibleRows; i++) {
            PlayerSocialManager.PlayerData req = incoming.get(i);
            int rowY = y + (i - start) * ROW_HEIGHT;

            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= width / 2 - 150 && mouseX < width / 2 + 100) {
                drawRect(width / 2 - 150, rowY, width / 2 + 100, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }

            drawString(fontRendererObj, req.name(), width / 2 - 146, rowY + 2, 0x55FF55);
        }
    }

    private void drawOutgoingRequests() {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot == null || boot.socialManager() == null) return;
        List<PlayerSocialManager.PlayerData> outgoing = boot.socialManager().getOutgoingRequests();

        if (outgoing.isEmpty()) {
            drawCenteredString(fontRendererObj, "No outgoing requests", width / 2, LIST_Y_START + 20, 0x808080);
            return;
        }

        int y = LIST_Y_START;
        for (int i = 0; i < outgoing.size(); i++) {
            PlayerSocialManager.PlayerData req = outgoing.get(i);
            drawString(fontRendererObj, req.name(), width / 2 - 146, y + i * ROW_HEIGHT + 2, 0xFFFF55);
        }
    }

    private List<PlayerSocialManager.PlayerData> getFilteredFriends() {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot == null || boot.socialManager() == null) return new ArrayList<>();

        List<PlayerSocialManager.PlayerData> friends = boot.socialManager().getFriends();
        String filter = (searchBox != null) ? searchBox.getText().trim().toLowerCase() : "";
        if (filter.isEmpty()) return friends;

        List<PlayerSocialManager.PlayerData> filtered = new ArrayList<>();
        for (PlayerSocialManager.PlayerData f : friends) {
            if (f.name().toLowerCase().contains(filter)) {
                filtered.add(f);
            }
        }
        return filtered;
    }

    private MinecraftBridge bridge() {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        return boot != null ? boot.bridge() : null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = org.lwjgl.input.Mouse.getEventDWheel();
        if (scroll > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        else if (scroll < 0) scrollOffset++;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot != null && boot.socialManager() != null) {
            if (updateListener != null) {
                boot.socialManager().removeFriendListUpdateListener(updateListener);
            }
            if (presenceListener != null) {
                boot.socialManager().getPresenceHandler().removePresenceListener(presenceListener);
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
