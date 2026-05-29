package dev.msf.friends.screen;

import com.mojang.authlib.yggdrasil.FriendsService;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.jspecify.annotations.Nullable;

/**
 * Standalone MSF Friends options screen for MC 1.16.5.
 * Since 1.16.5 lacks OnlineOptionsScreen/SimpleOptionsScreen, this screen
 * provides all friend feature, notification, and TURN options as direct buttons.
 */
public class MsfFriendsOptionsScreen extends Screen {

    private final Screen parent;

    // Friend feature buttons
    private @Nullable ButtonWidget enableFriendsBtn;
    private @Nullable ButtonWidget allowRequestsBtn;
    private @Nullable ButtonWidget presenceSharingBtn;
    private @Nullable ButtonWidget hiddenModeBtn;
    private @Nullable ButtonWidget refreshBtn;
    private @Nullable ButtonWidget openFriendsBtn;
    // Notification buttons
    private @Nullable ButtonWidget notifyOnlineBtn;
    private @Nullable ButtonWidget notifyStatusBtn;
    private @Nullable ButtonWidget notifyInviteBtn;
    private @Nullable ButtonWidget notifyJoinReqBtn;
    // TURN buttons
    private @Nullable ButtonWidget turnModeBtn;
    private @Nullable ButtonWidget iceModeBtn;
    // Status message
    private @Nullable Text statusMsg;
    private int statusColor = 0xFFFFFFFF;

    public MsfFriendsOptionsScreen(Screen parent) {
        super(new TranslatableText("options.msf_friends.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int colW = 150;
        int colGap = 4;
        int leftX = centerX - colW - colGap / 2;
        int rightX = centerX + colGap / 2;
        int rowH = 20;
        int rowGap = 4;
        int y = 32;

        // Section: Friend features
        // Header "好友功能"
        y += rowH + rowGap;

        enableFriendsBtn = new ButtonWidget(leftX, y, colW, rowH, LiteralText.EMPTY, b -> msf$toggleFriendFeature());
        allowRequestsBtn = new ButtonWidget(rightX, y, colW, rowH, LiteralText.EMPTY, b -> msf$toggleAllowFriendRequests());
        this.addButton(enableFriendsBtn);
        this.addButton(allowRequestsBtn);
        y += rowH + rowGap;

        presenceSharingBtn = new ButtonWidget(leftX, y, colW, rowH, LiteralText.EMPTY, b -> msf$cyclePresenceSharing());
        hiddenModeBtn = new ButtonWidget(rightX, y, colW, rowH, LiteralText.EMPTY, b -> msf$toggleHiddenMode());
        this.addButton(presenceSharingBtn);
        this.addButton(hiddenModeBtn);
        y += rowH + rowGap;

        refreshBtn = new ButtonWidget(leftX, y, colW, rowH, LiteralText.EMPTY, b -> msf$forceRefresh());
        openFriendsBtn = new ButtonWidget(rightX, y, colW, rowH, LiteralText.EMPTY, b -> msf$openFriends());
        this.addButton(refreshBtn);
        this.addButton(openFriendsBtn);
        y += rowH + rowH + rowGap; // Extra gap for section header

        // Section: Notifications
        // Header "通知设置"
        y += rowH + rowGap;

        notifyOnlineBtn = new ButtonWidget(leftX, y, colW, rowH, LiteralText.EMPTY, b -> {
            NotificationPrefs p = NotificationPrefs.get();
            p.notifyOnline = !p.notifyOnline;
            p.save();
            msf$syncButtonLabels();
        });
        notifyStatusBtn = new ButtonWidget(rightX, y, colW, rowH, LiteralText.EMPTY, b -> {
            NotificationPrefs p = NotificationPrefs.get();
            p.notifyStatus = !p.notifyStatus;
            p.save();
            msf$syncButtonLabels();
        });
        this.addButton(notifyOnlineBtn);
        this.addButton(notifyStatusBtn);
        y += rowH + rowGap;

        notifyInviteBtn = new ButtonWidget(leftX, y, colW, rowH, LiteralText.EMPTY, b -> {
            NotificationPrefs p = NotificationPrefs.get();
            p.notifyInvite = !p.notifyInvite;
            p.save();
            msf$syncButtonLabels();
        });
        notifyJoinReqBtn = new ButtonWidget(rightX, y, colW, rowH, LiteralText.EMPTY, b -> {
            NotificationPrefs p = NotificationPrefs.get();
            p.notifyJoinRequest = !p.notifyJoinRequest;
            p.save();
            msf$syncButtonLabels();
        });
        this.addButton(notifyInviteBtn);
        this.addButton(notifyJoinReqBtn);
        y += rowH + rowH + rowGap; // Extra gap for section header

        // Section: TURN settings
        // Header "TURN加速设置"
        y += rowH + rowGap;

        turnModeBtn = new ButtonWidget(leftX, y, colW, rowH, LiteralText.EMPTY, b -> msf$cycleTurnMode());
        iceModeBtn = new ButtonWidget(rightX, y, colW, rowH, LiteralText.EMPTY, b -> msf$cycleIceMode());
        this.addButton(turnModeBtn);
        this.addButton(iceModeBtn);
        y += rowH + rowGap;

        // Done button
        this.addButton(new ButtonWidget(centerX - 100, this.height - 32, 200, 20,
                new TranslatableText("gui.done"), b -> {
            if (this.client != null) {
                this.client.openScreen(this.parent);
            }
        }));

        msf$syncButtonLabels();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);

        int centerX = this.width / 2;
        int colW = 150;
        int colGap = 4;
        int leftX = centerX - colW - colGap / 2;
        int rightX = centerX + colGap / 2;
        int rowH = 20;
        int rowGap = 4;
        int y = 32;

        // Section headers
        if (this.textRenderer != null) {
            DrawableHelper.drawCenteredText(matrices, this.textRenderer, "好友功能", centerX, y + 5, 0xFFFFFF);
            y += rowH + rowGap;
            y += rowH + rowGap; // row 1
            y += rowH + rowGap; // row 2
            y += rowH + rowH + rowGap; // row 3 + gap

            DrawableHelper.drawCenteredText(matrices, this.textRenderer, "通知设置", centerX, y + 5, 0xFFFFFF);
            y += rowH + rowGap;
            y += rowH + rowGap; // row 1
            y += rowH + rowH + rowGap; // row 2 + gap

            DrawableHelper.drawCenteredText(matrices, this.textRenderer, "TURN加速设置", centerX, y + 5, 0xFFFFFF);

            // Status message
            if (statusMsg != null) {
                int statusW = this.textRenderer.getWidth(statusMsg);
                this.textRenderer.drawWithShadow(matrices, statusMsg,
                        centerX - statusW / 2.0F, this.height - 48, statusColor);
            }
        }

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        // cleanup
    }

    private void msf$syncButtonLabels() {
        PlayerSocialManager social = msf$social();
        MinecraftBridge bridge = msf$bridge();
        boolean ready = social != null && bridge != null;

        if (enableFriendsBtn != null) {
            enableFriendsBtn.setMessage(new LiteralText("启用好友功能: " + msf$friendFeatureLabel()));
            enableFriendsBtn.active = ready;
        }
        if (allowRequestsBtn != null) {
            allowRequestsBtn.setMessage(new LiteralText("好友请求: " + msf$allowRequestsLabel()));
            allowRequestsBtn.active = ready;
        }
        if (presenceSharingBtn != null) {
            presenceSharingBtn.setMessage(new LiteralText("在线状态共享: " + msf$presenceSharingLabel()));
            presenceSharingBtn.active = ready;
        }
        if (hiddenModeBtn != null) {
            hiddenModeBtn.setMessage(new LiteralText("隐身模式: " + msf$hiddenModeLabel()));
            hiddenModeBtn.active = ready;
        }
        if (refreshBtn != null) {
            refreshBtn.setMessage(new LiteralText("立即刷新"));
            refreshBtn.active = social != null;
        }
        if (openFriendsBtn != null) {
            openFriendsBtn.setMessage(new LiteralText("打开好友界面"));
            openFriendsBtn.active = this.client != null;
        }

        NotificationPrefs prefs = NotificationPrefs.get();
        if (notifyOnlineBtn != null) {
            notifyOnlineBtn.setMessage(new LiteralText("好友上线通知: " + msf$onOff(prefs.notifyOnline)));
        }
        if (notifyStatusBtn != null) {
            notifyStatusBtn.setMessage(new LiteralText("游戏状态通知: " + msf$onOff(prefs.notifyStatus)));
        }
        if (notifyInviteBtn != null) {
            notifyInviteBtn.setMessage(new LiteralText("邀请通知: " + msf$onOff(prefs.notifyInvite)));
        }
        if (notifyJoinReqBtn != null) {
            notifyJoinReqBtn.setMessage(new LiteralText("加入申请通知: " + msf$onOff(prefs.notifyJoinRequest)));
        }

        if (turnModeBtn != null) {
            turnModeBtn.setMessage(new LiteralText("国内TURN加速: " + TurnPrefs.get().turnMode.displayName()));
        }
        if (iceModeBtn != null) {
            iceModeBtn.setMessage(new LiteralText("连接模式: " + TurnPrefs.get().iceMode.displayName()));
        }
    }

    private String msf$friendFeatureLabel() {
        PlayerSocialManager social = msf$social();
        return social != null ? msf$onOff(social.isFriendListEnabled()) : "未就绪";
    }

    private String msf$allowRequestsLabel() {
        PlayerSocialManager social = msf$social();
        return social != null ? (social.isAllowFriendRequests() ? "允许" : "拒绝") : "未就绪";
    }

    private String msf$presenceSharingLabel() {
        MinecraftBridge bridge = msf$bridge();
        return bridge != null ? msf$sharingLabel(bridge.presenceSharing()) : "未就绪";
    }

    private String msf$hiddenModeLabel() {
        MinecraftBridge bridge = msf$bridge();
        return bridge != null ? msf$onOff(bridge.hiddenMode()) : "未就绪";
    }

    private @Nullable PlayerSocialManager msf$social() {
        var client = MsfFriendsBoot.get();
        return client != null ? client.social() : null;
    }

    private @Nullable MinecraftBridge msf$bridge() {
        var client = MsfFriendsBoot.get();
        return client != null ? client.bridge() : null;
    }

    private void msf$toggleFriendFeature() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) return;
        msf$saveFriendSettings(!client.social().isFriendListEnabled(), client.social().isAllowFriendRequests());
    }

    private void msf$toggleAllowFriendRequests() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) return;
        msf$saveFriendSettings(client.social().isFriendListEnabled(), !client.social().isAllowFriendRequests());
    }

    private void msf$saveFriendSettings(boolean fle, boolean afr) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.client == null) {
            msf$setStatus("好友服务未就绪", 0xFFFF8080);
            return;
        }
        msf$setStatus("正在保存设置...", 0xFFE0E0E0);
        client.social().updateFriendSettings(fle, afr).whenComplete((result, error) ->
                this.client.execute(() -> {
                    if (error != null) {
                        msf$setStatus("保存失败", 0xFFFF8080);
                    } else {
                        msf$applyResult("设置已保存", result);
                    }
                    msf$syncButtonLabels();
                }));
    }

    private void msf$cyclePresenceSharing() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null) return;
        MinecraftBridge.PresenceSharing next = switch (client.bridge().presenceSharing()) {
            case ALL     -> MinecraftBridge.PresenceSharing.LIMITED;
            case LIMITED -> MinecraftBridge.PresenceSharing.NONE;
            case NONE    -> MinecraftBridge.PresenceSharing.ALL;
        };
        client.bridge().setPresenceSharingMode(next);
        if (client.social() != null) client.social().getPresenceHandler().tryUpdatePresence();
        msf$setStatus("在线状态共享: " + msf$sharingLabel(next), 0xFF55FF55);
        msf$syncButtonLabels();
    }

    private void msf$toggleHiddenMode() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null || client.social() == null) return;
        boolean next = !client.bridge().hiddenMode();
        client.social().getPresenceHandler().setHiddenMode(next);
        msf$setStatus(next ? "隐身模式已开启" : "隐身模式已关闭", 0xFF55FF55);
        msf$syncButtonLabels();
    }

    private void msf$forceRefresh() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.client == null) return;
        msf$setStatus("正在刷新...", 0xFFE0E0E0);
        client.social().getPresenceHandler().tryUpdatePresence();
        client.social().getPresenceHandler().tick();
        client.social().getRemoteFriendListUpdateHandler().forceUpdate().whenComplete((ignored, error) ->
                this.client.execute(() -> {
                    msf$setStatus(error != null ? "刷新失败" : "已刷新", error != null ? 0xFFFF8080 : 0xFF55FF55);
                    msf$syncButtonLabels();
                }));
    }

    private void msf$openFriends() {
        if (this.client != null) {
            this.client.openScreen(new FriendsScreen(this));
        }
    }

    private void msf$applyResult(String successMsg, FriendsService.ResultCode result) {
        switch (result) {
            case SUCCESS              -> msf$setStatus(successMsg, 0xFF55FF55);
            case TOO_MANY_REQUESTS    -> msf$setStatus("操作过于频繁，请稍后再试", 0xFFFFAA00);
            case UNKNOWN_PROFILE      -> msf$setStatus("找不到该玩家", 0xFFFF8080);
            case FORBIDDEN            -> msf$setStatus("服务拒绝了本次操作", 0xFFFF8080);
            case SERVICE_NOT_AVAILABLE, TEMPORARY_UNAVAILABLE -> msf$setStatus("好友服务当前不可用", 0xFFFF8080);
            case CONNECTION_ISSUE     -> msf$setStatus("网络连接异常", 0xFFFF8080);
            case UPGRADE_NEEDED       -> msf$setStatus("当前客户端版本不支持此操作", 0xFFFF8080);
            default                   -> msf$setStatus("操作失败", 0xFFFF8080);
        }
    }

    private void msf$setStatus(String message, int color) {
        this.statusMsg   = new LiteralText(message);
        this.statusColor = color;
    }

    private static String msf$onOff(boolean v) { return v ? "开启" : "关闭"; }

    private void msf$cycleIceMode() {
        TurnPrefs prefs = TurnPrefs.get();
        prefs.iceMode = prefs.iceMode.next();
        prefs.save();
        msf$syncButtonLabels();
    }

    private void msf$cycleTurnMode() {
        TurnPrefs prefs = TurnPrefs.get();
        prefs.turnMode = prefs.turnMode.next();
        prefs.save();
        msf$syncButtonLabels();
    }

    private static String msf$sharingLabel(MinecraftBridge.PresenceSharing s) {
        return switch (s) {
            case ALL     -> "全部";
            case LIMITED -> "仅在线";
            case NONE    -> "隐藏";
        };
    }
}
