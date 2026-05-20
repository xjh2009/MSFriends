package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.p2p.SignalingException;
import dev.msf.friends.util.Logging;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public class P2PConnectScreen extends Screen {
    private static final Logger LOGGER = Logging.get();
    private static final Component CONNECTING = tr("screen.msf_friends.p2p.connecting");
    private static final Component FAILURE_GENERIC = tr("screen.msf_friends.p2p.failure.generic");
    private static final Component FAILURE_TIMEOUT = tr("screen.msf_friends.p2p.failure.timeout");
    private static final Component FAILURE_UNREACHABLE = tr("screen.msf_friends.p2p.failure.unreachable");
    private static final Component FAILURE_SIGNALING = tr("screen.msf_friends.p2p.failure.signaling");
    private final @Nullable Screen parent;
    private final String peerPmid;
    private Component status = CONNECTING;
    private boolean cancelled;

    private P2PConnectScreen(@Nullable Screen parent, String peerPmid) {
        super(GameNarrator.NO_TITLE);
        this.parent = parent;
        this.peerPmid = peerPmid;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static void startConnecting(@Nullable Screen parent, Minecraft minecraft, String peerPmid) {
        P2PConnectScreen screen = new P2PConnectScreen(parent, peerPmid);
        minecraft.prepareForMultiplayer();
        minecraft.setScreen(screen);
        screen.connect(minecraft);
    }

    private void connect(Minecraft minecraft) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.p2p() == null) {
            minecraft.setScreen(new DisconnectedScreen(this.parent,
                    CommonComponents.CONNECT_FAILED, FAILURE_GENERIC));
            return;
        }
        client.p2p().ensureSignalingConnected();
        client.p2p().joinPlayer(this.peerPmid).whenComplete((result, error) -> minecraft.execute(() -> {
            if (!this.cancelled && minecraft.screen == this) {
                if (error != null) {
                    LOGGER.warn("[p2p-screen] connection failed: {}", error.getMessage());
                    minecraft.setScreen(new DisconnectedScreen(this.parent,
                            CommonComponents.CONNECT_FAILED, reasonFor(error)));
                } else {
                    this.status = tr("screen.msf_friends.p2p.joining");
                }
            }
        }));
    }

    private static Component reasonFor(Throwable error) {
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
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> {
            this.cancelled = true;
            var client = MsfFriendsBoot.get();
            if (client != null && client.p2p() != null) {
                client.p2p().cancelOutgoingJoins();
            }
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20).build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.status, this.width / 2, this.height / 2 - 50, -1);
    }

    public static void show(@Nullable Screen parent, String peerPmid) {
        startConnecting(parent, Minecraft.getInstance(), peerPmid);
    }
}
