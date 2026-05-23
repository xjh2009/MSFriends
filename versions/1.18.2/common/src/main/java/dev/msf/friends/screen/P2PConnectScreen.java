package dev.msf.friends.screen;

import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.FabricReflect;
import dev.msf.friends.p2p.SignalingException;
import dev.msf.friends.util.Logging;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * P2P connection screen for MC 1.18.2, adapted from 26.1.2.
 */
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
        super(new TextComponent(""));  // No title for 1.18.2
        this.parent = parent;
        this.peerPmid = peerPmid;
    }

    private static Component tr(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static void startConnecting(@Nullable Screen parent, net.minecraft.client.Minecraft minecraft, String peerPmid) {
        // Prepare for multiplayer — mirrors 26.2's call.
        // On 1.18.2 this may be a no-op on Fabric, but on Forge it initializes
        // the network subsystem. Use reflection via FabricReflect for compatibility.
        try {
            FabricReflect.mcMethod(minecraft.getClass(), "prepareForMultiplayer").invoke(minecraft);
        } catch (Exception e) {
            // May not exist on all 1.18.2 builds — safe to ignore
            LOGGER.debug("[p2p-screen] prepareForMultiplayer unavailable: {}", e.getMessage());
        }
        P2PConnectScreen screen = new P2PConnectScreen(parent, peerPmid);
        minecraft.setScreen(screen);
        screen.connect(minecraft);
    }

    private void connect(net.minecraft.client.Minecraft minecraft) {
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
        this.addRenderableWidget(new Button(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20,
                CommonComponents.GUI_CANCEL, btn -> {
            this.cancelled = true;
            var client = MsfFriendsBoot.get();
            if (client != null && client.p2p() != null) {
                client.p2p().cancelOutgoingJoins();
            }
            this.minecraft.setScreen(this.parent);
        }));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredString(matrices, this.font, this.status, this.width / 2, this.height / 2 - 50, -1);
        super.render(matrices, mouseX, mouseY, delta);
    }

    public static void show(@Nullable Screen parent, String peerPmid) {
        startConnecting(parent, net.minecraft.client.Minecraft.getInstance(), peerPmid);
    }
}
