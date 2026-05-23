package dev.msf.friends.screen;

import dev.msf.friends.bridge.FabricReflect;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.p2p.SignalingException;
import dev.msf.friends.util.Logging;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * P2P connection progress screen.
 *
 * <p>1.20.1 Yarn: GameNarrator.NO_TITLE → NarratorManager.EMPTY,
 * CommonComponents → ScreenTexts, GuiGraphics → DrawContext,
 * Button → ButtonWidget.
 *
 * <p>Matches 26.1.2's P2PConnectScreen exactly, including the
 * prepareForMultiplayer() call before starting connection.
 * In 1.20.1 this method may not have a Yarn name, so we call it
 * via reflection through FabricReflect.
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
        super(NarratorManager.EMPTY);
        this.parent = parent;
        this.peerPmid = peerPmid;
    }

    private static Text tr(String key, Object... args) {
        return Text.translatable(key, args);
    }

    public static void startConnecting(@Nullable Screen parent, MinecraftClient minecraft, String peerPmid) {
        P2PConnectScreen screen = new P2PConnectScreen(parent, peerPmid);
        // Mirror 26.1.2: calis method has no mapped name.
        // Call via FabricReflect (intermediary: method_45347 / class_310)
        try {
            var m = FabricReflect.mcMethod(
                net.minecraft.client.MinecraftClient.class, "prepareForMultiplayer");
            m.invoke(minecraft);
        } catch (Exception ignore) {
            // Method may not exist in this version — not critical for P2P{
            // Method may not exist in this version — not critical
        }
        minecraft.setScreen(screen);
        screen.connect(minecraft);
    }

    private void connect(MinecraftClient minecraft) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.p2p() == null) {
            minecraft.setScreen(new DisconnectedScreen(this.parent,
                    ScreenTexts.CONNECT_FAILED, FAILURE_GENERIC));
            return;
        }
        client.p2p().ensureSignalingConnected();
        client.p2p().joinPlayer(this.peerPmid).whenComplete((result, error) -> minecraft.execute(() -> {
            if (!this.cancelled && minecraft.currentScreen == this) {
                if (error != null) {
                    LOGGER.warn("[p2p-screen] connection failed: {}", error.getMessage());
                    minecraft.setScreen(new DisconnectedScreen(this.parent,
                            ScreenTexts.CONNECT_FAILED, reasonFor(error)));
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
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, btn -> {
            this.cancelled = true;
            var client = MsfFriendsBoot.get();
            if (client != null && client.p2p() != null) {
                client.p2p().cancelOutgoingJoins();
            }
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20).build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawTextWithShadow(this.textRenderer, this.status, this.width / 2 - this.textRenderer.getWidth(this.status) / 2, this.height / 2 - 50, -1);
        super.render(context, mouseX, mouseY, delta);
    }

    public static void show(@Nullable Screen parent, String peerPmid) {
        startConnecting(parent, MinecraftClient.getInstance(), peerPmid);
    }
}
