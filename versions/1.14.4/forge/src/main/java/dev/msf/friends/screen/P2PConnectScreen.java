package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.p2p.SignalingException;
import dev.msf.friends.util.Logging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ITextComponent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * P2P connection screen for MC 1.13.2 MCP.
 *
 * <p>1.13.2 API differences:
 * <ul>
 *   <li>{@code Screen} → {@code Screen}</li>
 *   <li>{@code DisconnectedScreen} → {@code DisconnectedScreen(Screen, String, ITextComponent)}</li>
 *   <li>{@code drawScreen} → {@code render(int, int, float)}</li>
 *   <li>{@code renderBackground()} → {@code drawDefaultBackground()}</li>
 *   <li>{@code minecraft.execute()} → {@code minecraft.execute()}</li>
 * </ul>
 */
public class P2PConnectScreen extends Screen {
    private static final Logger LOGGER = Logging.get();
    private static final ITextComponent CONNECTING = tr("screen.msf_friends.p2p.connecting");
    private static final ITextComponent FAILURE_GENERIC = tr("screen.msf_friends.p2p.failure.generic");
    private static final ITextComponent FAILURE_TIMEOUT = tr("screen.msf_friends.p2p.failure.timeout");
    private static final ITextComponent FAILURE_UNREACHABLE = tr("screen.msf_friends.p2p.failure.unreachable");
    private static final ITextComponent FAILURE_SIGNALING = tr("screen.msf_friends.p2p.failure.signaling");
    private final @Nullable Screen parent;
    private final String peerPmid;
    private ITextComponent status = CONNECTING;
    private boolean cancelled;

    private P2PConnectScreen(@Nullable Screen parent, String peerPmid) {
        super(new StringTextComponent("P2P Connect"));
        this.parent = parent;
        this.peerPmid = peerPmid;
    }

    private static ITextComponent tr(String key, Object... args) {
        return new TranslationTextComponent(key, args);
    }

    public static void startConnecting(@Nullable Screen parent, Minecraft minecraft, String peerPmid) {
        P2PConnectScreen screen = new P2PConnectScreen(parent, peerPmid);
        minecraft.displayGuiScreen(screen);
        screen.connect(minecraft);
    }

    private void connect(Minecraft minecraft) {
        MsfFriendsBoot client = MsfFriendsBoot.get();
        if (client == null || client.p2p() == null) {
            minecraft.displayGuiScreen(new DisconnectedScreen(this.parent,
                    "connect.failed", FAILURE_GENERIC));
            return;
        }
        client.p2p().ensureSignalingConnected();
        client.p2p().joinPlayer(this.peerPmid).whenComplete((result, error) -> minecraft.execute(() -> {
            if (!this.cancelled && minecraft.field_71462_r == this) {
                if (error != null) {
                    LOGGER.warn("[p2p-screen] connection failed: {}", error.getMessage());
                    minecraft.displayGuiScreen(new DisconnectedScreen(this.parent,
                            "connect.failed", reasonFor(error)));
                } else {
                    this.status = tr("screen.msf_friends.p2p.joining");
                }
            }
        }));
    }

    private static ITextComponent reasonFor(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        if (cause instanceof TimeoutException) return FAILURE_TIMEOUT;
        if (cause instanceof SignalingException.UnknownPlayerException) return FAILURE_UNREACHABLE;
        if (cause instanceof SignalingException.MessageUndeliveredException
                || cause instanceof SignalingException.SignalingAuthException
                || cause instanceof SignalingException.TurnAuthFailedException) return FAILURE_SIGNALING;
        return FAILURE_GENERIC;
    }

    @Override
    public void init(Minecraft mc, int w, int h) {
        super.init(mc, w, h);
        this.addButton(new SimpleButton(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20,
                new TranslationTextComponent("gui.cancel").getFormattedText(), () -> {
            this.cancelled = true;
            MsfFriendsBoot client = MsfFriendsBoot.get();
            if (client != null && client.p2p() != null) {
                client.p2p().cancelOutgoingJoins();
            }
            this.minecraft.displayGuiScreen(this.parent);
        }));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // GLFW_KEY_ESCAPE — prevent closing
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        this.drawCenteredString(this.font, this.status.getFormattedText(),
                this.width / 2, this.height / 2 - 50, -1);
        super.render(mouseX, mouseY, delta);
    }

    public static void show(@Nullable Screen parent, String peerPmid) {
        startConnecting(parent, Minecraft.getInstance(), peerPmid);
    }
}
