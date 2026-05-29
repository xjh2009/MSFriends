package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.p2p.SignalingException;
import dev.msf.friends.util.Logging;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * P2P connection screen for MC 1.14.4 Yarn.
 * No MatrixStack — uses GlStateManager for rendering.
 */
public class P2PConnectScreen extends Screen {
    private static final Logger LOGGER = Logging.get();
    private static final Text CONNECTING = tr("screen.msf_friends.p2p.connecting");
    private static final Text FAILURE_GENERIC = tr("screen.msf_friends.p2p.failure.generic");
    private static final Text FAILURE_TIMEOUT = tr("screen.msf_friends.p2p.failure.timeout");
    private static final Text FAILURE_UNREACHABLE = tr("screen.msf_friends.p2p.failure.unreachable");
    private static final Text FAILURE_SIGNALING = tr("screen.msf_friends.p2p.failure.signaling");
    private final @Nullable Screen parent;
    private final String peerPmid;
    private Text status = CONNECTING;
    private boolean cancelled;

    private P2PConnectScreen(@Nullable Screen parent, String peerPmid) {
        super(new LiteralText(""));
        this.parent = parent;
        this.peerPmid = peerPmid;
    }

    private static Text tr(String key, Object... args) {
        return new TranslatableText(key, args);
    }

    public static void startConnecting(@Nullable Screen parent, net.minecraft.client.MinecraftClient minecraft, String peerPmid) {
        P2PConnectScreen screen = new P2PConnectScreen(parent, peerPmid);
        minecraft.openScreen(screen);
        screen.connect(minecraft);
    }

    private void connect(net.minecraft.client.MinecraftClient minecraft) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.p2p() == null) {
            minecraft.openScreen(new DisconnectedScreen(this.parent,
                    "connect.failed", FAILURE_GENERIC));
            return;
        }
        client.p2p().ensureSignalingConnected();
        client.p2p().joinPlayer(this.peerPmid).whenComplete((result, error) -> minecraft.execute(() -> {
            if (!this.cancelled && minecraft.currentScreen == this) {
                if (error != null) {
                    LOGGER.warn("[p2p-screen] connection failed: {}", error.getMessage());
                    minecraft.openScreen(new DisconnectedScreen(this.parent,
                            "connect.failed", reasonFor(error)));
                } else {
                    this.status = tr("screen.msf_friends.p2p.joining");
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
        this.addButton(new ButtonWidget(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20,
                "取消", btn -> {
            this.cancelled = true;
            var client = MsfFriendsBoot.get();
            if (client != null && client.p2p() != null) {
                client.p2p().cancelOutgoingJoins();
            }
            this.minecraft.openScreen(this.parent);
        }));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(int mouseX, int mouseY, float a) {
        this.renderBackground();
        TextRenderer tr = this.minecraft.textRenderer;
        drawCenteredString(tr, this.status.asString(), this.width / 2, this.height / 2 - 50, -1);
        super.render(mouseX, mouseY, a);
    }

    public static void show(@Nullable Screen parent, String peerPmid) {
        startConnecting(parent, net.minecraft.client.MinecraftClient.getInstance(), peerPmid);
    }
}
