package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.MsfFriendsConstants;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * P2P Connect screen for MC 1.8.8.
 * Enter a friend's UUID and connect via WebRTC signaling.
 */
public class P2PConnectScreen extends GuiScreen {
    private static final Logger LOGGER = Logging.get();

    private final GuiScreen previous;
    private GuiTextField friendUuidField;
    private GuiButton btnConnect;
    private GuiButton btnBack;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;

    public P2PConnectScreen(GuiScreen previous) {
        this.previous = previous;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.friendUuidField = new GuiTextField(0, this.fontRendererObj,
                centerX - 100, centerY - 30, 200, 20);
        this.friendUuidField.setMaxStringLength(40);
        this.friendUuidField.setFocused(true);

        this.buttonList.add(this.btnConnect = new GuiButton(1, centerX - 100, centerY + 10, 200, 20, "Connect"));
        this.buttonList.add(this.btnBack = new GuiButton(2, centerX - 100, centerY + 40, 200, 20, "Back"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws java.io.IOException {
        if (button.id == 1) {
            String text = friendUuidField.getText().trim();
            try {
                UUID targetUuid = UUID.fromString(text);
                statusMessage = "Connecting...";
                statusColor = 0xFFFF55;
                MsfFriendsBoot boot = MsfFriendsBoot.get();
                if (boot != null && boot.p2p() != null) {
                    boot.p2p().joinPlayer(text);
                }
            } catch (IllegalArgumentException e) {
                statusMessage = "Invalid UUID format";
                statusColor = 0xFF5555;
            }
        } else if (button.id == 2) {
            mc.displayGuiScreen(previous);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (friendUuidField != null && friendUuidField.isFocused()) {
            friendUuidField.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(previous);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (friendUuidField != null) {
            friendUuidField.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int centerX = this.width / 2;

        this.drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.BOLD + "P2P Connect", centerX, 16, 0xFFFFFF);
        this.drawCenteredString(this.fontRendererObj,
                "Enter friend's UUID:", centerX, this.height / 2 - 45, 0xCCCCCC);

        if (friendUuidField != null) friendUuidField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (!statusMessage.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, statusMessage, centerX, this.height / 2 + 65, statusColor);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
