package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.MsfFriendsConstants;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.P2PManager;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Main friends screen for MC 1.8.8.
 * Shows friend list, controls, and P2P connection status.
 */
public class FriendsScreen extends GuiScreen {
    private static final Logger LOGGER = Logging.get();

    private final GuiScreen previous;
    private GuiTextField searchField;
    private GuiButton btnAddFriend;
    private GuiButton btnConnect;
    private GuiButton btnDisconnect;
    private GuiButton btnSettings;
    private int scrollOffset = 0;
    private boolean inited = false;

    public FriendsScreen(GuiScreen previous) {
        this.previous = previous;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        inited = true;

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Search field at top
        this.searchField = new GuiTextField(0, this.fontRendererObj,
                centerX - 100, 22, 200, 20);
        this.searchField.setMaxStringLength(50);
        this.searchField.setFocused(true);
        this.searchField.setEnabled(true);

        // Buttons at bottom
        int btnY = this.height - 30;
        this.buttonList.add(this.btnAddFriend = new GuiButton(1, centerX - 154, btnY, 100, 20, "Add Friend"));
        this.buttonList.add(this.btnConnect = new GuiButton(2, centerX - 50, btnY, 100, 20, "Connect"));
        this.buttonList.add(this.btnDisconnect = new GuiButton(3, centerX + 54, btnY, 100, 20, "Disconnect"));
        this.buttonList.add(this.btnSettings = new GuiButton(4, 4, btnY, 80, 20, "Settings"));

        updateButtonStates();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws java.io.IOException {
        if (button.enabled) {
            if (button.id == 1) {
                // Add Friend — send request
                String name = searchField.getText().trim();
                if (!name.isEmpty()) {
                    PlayerSocialManager social = MsfFriendsBoot.get().social();
                    if (social != null) {
                        social.sendFriendRequest(name);
                    }
                }
            } else if (button.id == 2) {
                // Connect — open P2P connect screen
                this.mc.displayGuiScreen(new P2PConnectScreen(this));
            } else if (button.id == 3) {
                // Disconnect
                MsfFriendsBoot boot = MsfFriendsBoot.get();
                if (boot != null && boot.p2p() != null) {
                    boot.p2p().shutdown();
                }
            } else if (button.id == 4) {
                // Settings — toggle presence
                MsfFriendsBoot boot = MsfFriendsBoot.get();
                if (boot != null && boot.bridge() != null) {
                    MinecraftBridge.PresenceSharing current = boot.bridge().presenceSharing();
                    MinecraftBridge.PresenceSharing[] vals = MinecraftBridge.PresenceSharing.values();
                    MinecraftBridge.PresenceSharing next = vals[(current.ordinal() + 1) % vals.length];
                    boot.bridge().setPresenceSharingMode(next);
                }
            }
            updateButtonStates();
        }
    }

    private void updateButtonStates() {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        boolean connected = boot != null && boot.p2p() != null && boot.p2p().hasOutgoingJoinRequest();
        if (btnConnect != null) btnConnect.enabled = !connected;
        if (btnDisconnect != null) btnDisconnect.enabled = connected;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (searchField != null && searchField.isFocused()) {
            searchField.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(previous);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Background
        this.drawDefaultBackground();

        // Title
        this.drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.BOLD + "MSF Friends",
                this.width / 2, 8, 0xFFFFFF);

        // Search field
        if (searchField != null) {
            searchField.drawTextBox();
        }

        // Friend list
        drawFriendList(mouseX, mouseY);

        // Connection status
        drawStatus();

        // Buttons
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawFriendList(int mouseX, int mouseY) {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        if (boot == null || boot.social() == null) {
            drawCenteredString(fontRendererObj, "Waiting for initialization...", width / 2, height / 2, 0x888888);
            return;
        }

        PlayerSocialManager social = boot.social();
        String filter = searchField != null ? searchField.getText().trim().toLowerCase() : "";

        // Get friend entries
        List<FriendEntry> friends = new ArrayList<>();
        // TODO: Populate from PlayerSocialManager friend lists
        // For now show placeholder entries
        friends.add(new FriendEntry("Example Friend", UUID.randomUUID(), true));

        int listX = width / 2 - 100;
        int listY = 48;
        int itemHeight = 24;
        int maxVisible = (height - 90) / itemHeight;

        for (int i = 0; i < Math.min(friends.size(), maxVisible); i++) {
            FriendEntry entry = friends.get(i);
            if (!filter.isEmpty() && !entry.name.toLowerCase().contains(filter)) continue;

            int y = listY + i * itemHeight;

            // Hover highlight
            if (mouseX >= listX && mouseX <= listX + 200 && mouseY >= y && mouseY <= y + itemHeight) {
                drawRect(listX, y, listX + 200, y + itemHeight, 0x33FFFFFF);
            }

            // Status indicator
            int statusColor = entry.online ? 0x55FF55 : 0xFF5555;
            drawRect(listX, y + 2, listX + 4, y + itemHeight - 2, statusColor);

            // Name
            fontRendererObj.drawStringWithShadow(entry.name, listX + 10, y + 4, 0xFFFFFF);
            fontRendererObj.drawStringWithShadow(
                    entry.online ? EnumChatFormatting.GREEN + "Online" : EnumChatFormatting.RED + "Offline",
                    listX + 10, y + 14, 0x888888);
        }

        if (friends.isEmpty()) {
            drawCenteredString(fontRendererObj, "No friends yet. Add some!", width / 2, listY + 20, 0x888888);
        }
    }

    private void drawStatus() {
        MsfFriendsBoot boot = MsfFriendsBoot.get();
        String status = "Initializing...";
        int color = 0xFFFF55;

        if (boot != null && boot.p2p() != null) {
            P2PManager p2p = boot.p2p();
            if (p2p.hasOutgoingJoinRequest()) {
                status = "Connected";
                color = 0x55FF55;
            } else {
                status = "Ready";
                color = 0x55FF55;
            }
        } else if (boot != null) {
            status = "Starting...";
        }

        fontRendererObj.drawStringWithShadow("Status: " + status, 4, this.height - 12, color);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static class FriendEntry {
        final String name;
        final UUID uuid;
        final boolean online;
        FriendEntry(String name, UUID uuid, boolean online) {
            this.name = name;
            this.uuid = uuid;
            this.online = online;
        }
    }
}
