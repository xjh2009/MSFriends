package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.p2p.SignalingException;
import dev.msf.friends.util.Logging;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * P2P connection screen — shows "Connecting..." while establishing a
 * WebRTC P2P connection, adapted for 1.16.5 Yarn API.
 */
public class P2PConnectScreen extends Screen {
    private static final Logger LOGGER = Logging.get();
    private static final Text CONNECTING = new TranslatableText("screen.msf_friends.p2p.connecting");
    private static final Text FAILURE_GENERIC = new TranslatableText("screen.msf_friends.p2p.failure.generic");
    private static final Text FAILURE_TIMEOUT = new TranslatableText("screen.msf_friends.p2p.failure.timeout");
    private static final Text FAILURE_UNREACHABLE = new TranslatableText("screen.msf_friends.p2p.failure.unreachable");
    private static final Text FAILURE_SIGNALING = new TranslatableText("screen.msf_friends.p2p.failure.signaling");
    private final @Nullable Screen parent;
    private final String peerPmid;
    private Text status = CONNECTING;
    private boolean cancelled;

    private P2PConnectScreen(@Nullable Screen parent, String peerPmid) {
        super(new TranslatableText("")); // No title
        this.parent = parent;
        this.peerPmid = peerPmid;
    }

    public static void startConnecting(@Nullable Screen parent, MinecraftClient minecraft, String peerPmid) {
        P2PConnectScreen screen = new P2PConnectScreen(parent, peerPmid);
        minecraft.openScreen(screen);
        screen.connect(minecraft);
    }

    private void connect(MinecraftClient minecraft) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.p2p() == null) {
            minecraft.openScreen(new DisconnectedScreen(this.parent,
                    new TranslatableText("connect.failed"), FAILURE_GENERIC));
            return;
        }
        client.p2p().ensureSignalingConnected();
        client.p2p().joinPlayer(this.peerPmid).whenComplete((result, error) -> minecraft.execute(() -> {
            if (!this.cancelled && minecraft.currentScreen == this) {
                if (error != null) {
                    LOGGER.warn("[p2p-screen] connection failed: {}", error.getMessage());
                    minecraft.openScreen(new DisconnectedScreen(this.parent,
                            new TranslatableText("connect.failed"), reasonFor(error)));
                } else {
                    this.status = new TranslatableText("screen.msf_friends.p2p.joining");
                }
            }
        }));
    }

    private static Text reasonFor(Throwable error) {
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
    protected void init() {
        super.init();
        this.addButton(new ButtonWidget(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20,
                new TranslatableText("gui.cancel"), btn -> {
                    this.cancelled = true;
                    var client = MsfFriendsBoot.get();
                    if (client != null && client.p2p() != null) {
                        client.p2p().cancelOutgoingJoins();
                    }
                    if (this.client != null) {
                        this.client.openScreen(this.parent);
                    }
                }));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.status, this.width / 2, this.height / 2 - 50, -1);
        super.render(matrices, mouseX, mouseY, delta);
    }

    public static void show(@Nullable Screen parent, String peerPmid) {
        startConnecting(parent, MinecraftClient.getInstance(), peerPmid);
    }
}
