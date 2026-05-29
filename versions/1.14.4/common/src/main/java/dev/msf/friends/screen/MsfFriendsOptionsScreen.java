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
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.jspecify.annotations.Nullable;

/**
 * Standalone MSF Friends options screen for MC 1.14.4.
 * No MatrixStack — uses GlStateManager for rendering.
 * Uses switch statements instead of switch expressions.
 */
public class MsfFriendsOptionsScreen extends Screen {

    private final Screen parent;

    private @Nullable ButtonWidget enableFriendsBtn;
    private @Nullable ButtonWidget allowRequestsBtn;
    private @Nullable ButtonWidget presenceSharingBtn;
    private @Nullable ButtonWidget hiddenModeBtn;
    private @Nullable ButtonWidget refreshBtn;
    private @Nullable ButtonWidget openFriendsBtn;
    private @Nullable ButtonWidget notifyOnlineBtn;
    private @Nullable ButtonWidget notifyStatusBtn;
    private @Nullable ButtonWidget notifyInviteBtn;
    private @Nullable ButtonWidget notifyJoinReqBtn;
    private @Nullable ButtonWidget turnModeBtn;
    private @Nullable ButtonWidget iceModeBtn;
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

        y += rowH + rowGap;

        enableFriendsBtn = new ButtonWidget(leftX, y, colW, rowH, "", b -> msf$toggleFriendFeature());
        allowRequestsBtn = new ButtonWidget(rightX, y, colW, rowH, "", b -> msf$toggleAllowFriendRequests());
        this.addButton(enableFriendsBtn);
        this.addButton(allowRequestsBtn);
        y += rowH + rowGap;

        presenceSharingBtn = new ButtonWidget(leftX, y, colW, rowH, "", b -> msf$cyclePresenceSharing());
        hiddenModeBtn = new ButtonWidget(rightX, y, colW, rowH, "", b -> msf$toggleHiddenMode());
        this.addButton(presenceSharingBtn);
        this.addButton(hiddenModeBtn);
        y += rowH + rowGap;

        refreshBtn = new ButtonWidget(leftX, y, colW, rowH, "", b -> msf$forceRefresh());
        openFriendsBtn = new ButtonWidget(rightX, y, colW, rowH, "", b -> msf$openFriends());
        this.addButton(refreshBtn);
        this.addButton(openFriendsBtn);
        y += rowH + rowH + rowGap;

        y += rowH + rowGap;

        notifyOnlineBtn = new ButtonWidget(leftX, y, colW, rowH, "", b -> {
            NotificationPrefs p = NotificationPrefs.get();
            p.notifyOnline = !p.notifyOnline;
            p.save();
            msf$syncButtonLabels();
        });
        notifyStatusBtn = new ButtonWidget(rightX, y, colW, rowH, "", b -> {
            NotificationPrefs p = NotificationPrefs.get();
            p.notifyStatus = !p.notifyStatus;
            p.save();
            msf$syncButtonLabels();
        });
        this.addButton(notifyOnlineBtn);
        this.addButton(notifyStatusBtn);
        y += rowH + rowGap;

        notifyInviteBtn = new ButtonWidget(leftX, y, colW, rowH, "", b -> {
            NotificationPrefs p = NotificationPrefs.get();
            p.notifyInvite = !p.notifyInvite;
            p.save();
            msf$syncButtonLabels();
        });
        notifyJoinReqBtn = new ButtonWidget(rightX, y, colW, rowH, "", b -> {
            NotificationPrefs p = NotificationPrefs.get();
            p.notifyJoinRequest = !p.notifyJoinRequest;
            p.save();
            msf$syncButtonLabels();
        });
        this.addButton(notifyInviteBtn);
        this.addButton(notifyJoinReqBtn);
        y += rowH + rowH + rowGap;

        y += rowH + rowGap;

        turnModeBtn = new ButtonWidget(leftX, y, colW, rowH, "", b -> msf$cycleTurnMode());
        iceModeBtn = new ButtonWidget(rightX, y, colW, rowH, "", b -> msf$cycleIceMode());
        this.addButton(turnModeBtn);
        this.addButton(iceModeBtn);
        y += rowH + rowGap;

        this.addButton(new ButtonWidget(centerX - 100, this.height - 32, 200, 20,
                new TranslatableText("gui.done").getString(), b -> {
            if (this.minecraft != null) {
                this.minecraft.openScreen(this.parent);
            }
        }));

        msf$syncButtonLabels();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();

        int centerX = this.width / 2;
        int colW = 150;
        int colGap = 4;
        int rowH = 20;
        int rowGap = 4;
        int y = 32;

        if (this.minecraft.textRenderer != null) {
            drawCenteredString(this.minecraft.textRenderer, "好友功能", centerX, y + 5, 0xFFFFFF);
            y += rowH + rowGap;
            y += rowH + rowGap;
            y += rowH + rowGap;
            y += rowH + rowH + rowGap;

            drawCenteredString(this.minecraft.textRenderer, "通知设置", centerX, y + 5, 0xFFFFFF);
            y += rowH + rowGap;
            y += rowH + rowGap;
            y += rowH + rowH + rowGap;

            drawCenteredString(this.minecraft.textRenderer, "TURN加速设置", centerX, y + 5, 0xFFFFFF);

            if (statusMsg != null) {
                int statusW = this.minecraft.textRenderer.getStringWidth(statusMsg.asString());
                this.minecraft.textRenderer.drawWithShadow(statusMsg.asString(),
                        centerX - statusW / 2.0F, this.height - 48, statusColor);
            }
        }

        super.render(mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
    }

    private void msf$syncButtonLabels() {
        PlayerSocialManager social = msf$social();
        MinecraftBridge bridge = msf$bridge();
        boolean ready = social != null && bridge != null;

        if (enableFriendsBtn != null) {
            enableFriendsBtn.setMessage("启用好友功能: " + msf$friendFeatureLabel());
            enableFriendsBtn.active = ready;
        }
        if (allowRequestsBtn != null) {
            allowRequestsBtn.setMessage("好友请求: " + msf$allowRequestsLabel());
            allowRequestsBtn.active = ready;
        }
        if (presenceSharingBtn != null) {
            presenceSharingBtn.setMessage("在线状态共享: " + msf$presenceSharingLabel());
            presenceSharingBtn.active = ready;
        }
        if (hiddenModeBtn != null) {
            hiddenModeBtn.setMessage("隐身模式: " + msf$hiddenModeLabel());
            hiddenModeBtn.active = ready;
        }
        if (refreshBtn != null) {
            refreshBtn.setMessage("立即刷新");
            refreshBtn.active = social != null;
        }
        if (openFriendsBtn != null) {
            openFriendsBtn.setMessage("打开好友界面");
            openFriendsBtn.active = this.minecraft != null;
        }

        NotificationPrefs prefs = NotificationPrefs.get();
        if (notifyOnlineBtn != null) {
            notifyOnlineBtn.setMessage("好友上线通知: " + msf$onOff(prefs.notifyOnline));
        }
        if (notifyStatusBtn != null) {
            notifyStatusBtn.setMessage("游戏状态通知: " + msf$onOff(prefs.notifyStatus));
        }
        if (notifyInviteBtn != null) {
            notifyInviteBtn.setMessage("邀请通知: " + msf$onOff(prefs.notifyInvite));
        }
        if (notifyJoinReqBtn != null) {
            notifyJoinReqBtn.setMessage("加入申请通知: " + msf$onOff(prefs.notifyJoinRequest));
        }

        if (turnModeBtn != null) {
            turnModeBtn.setMessage("国内TURN加速: " + TurnPrefs.get().turnMode.displayName());
        }
        if (iceModeBtn != null) {
            iceModeBtn.setMessage("连接模式: " + TurnPrefs.get().iceMode.displayName());
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
        if (client == null || client.social() == null || this.minecraft == null) {
            msf$setStatus("好友服务未就绪", 0xFFFF8080);
            return;
        }
        msf$setStatus("正在保存设置...", 0xFFE0E0E0);
        client.social().updateFriendSettings(fle, afr).whenComplete((result, error) ->
                this.minecraft.execute(() -> {
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
        MinecraftBridge.PresenceSharing current = client.bridge().presenceSharing();
        MinecraftBridge.PresenceSharing next;
        switch (current) {
            case ALL: next = MinecraftBridge.PresenceSharing.LIMITED; break;
            case LIMITED: next = MinecraftBridge.PresenceSharing.NONE; break;
            default: next = MinecraftBridge.PresenceSharing.ALL; break;
        }
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
        if (client == null || client.social() == null || this.minecraft == null) return;
        msf$setStatus("正在刷新...", 0xFFE0E0E0);
        client.social().getPresenceHandler().tryUpdatePresence();
        client.social().getPresenceHandler().tick();
        client.social().getRemoteFriendListUpdateHandler().forceUpdate().whenComplete((ignored, error) ->
                this.minecraft.execute(() -> {
                    msf$setStatus(error != null ? "刷新失败" : "已刷新", error != null ? 0xFFFF8080 : 0xFF55FF55);
                    msf$syncButtonLabels();
                }));
    }

    private void msf$openFriends() {
        if (this.minecraft != null) {
            this.minecraft.openScreen(new FriendsScreen(this));
        }
    }

    private void msf$applyResult(String successMsg, FriendsService.ResultCode result) {
        switch (result) {
            case SUCCESS: msf$setStatus(successMsg, 0xFF55FF55); break;
            case TOO_MANY_REQUESTS: msf$setStatus("操作过于频繁，请稍后再试", 0xFFFFAA00); break;
            case UNKNOWN_PROFILE: msf$setStatus("找不到该玩家", 0xFFFF8080); break;
            case FORBIDDEN: msf$setStatus("服务拒绝了本次操作", 0xFFFF8080); break;
            case SERVICE_NOT_AVAILABLE: case TEMPORARY_UNAVAILABLE: msf$setStatus("好友服务当前不可用", 0xFFFF8080); break;
            case CONNECTION_ISSUE: msf$setStatus("网络连接异常", 0xFFFF8080); break;
            case UPGRADE_NEEDED: msf$setStatus("当前客户端版本不支持此操作", 0xFFFF8080); break;
            default: msf$setStatus("操作失败", 0xFFFF8080); break;
        }
    }

    private void msf$setStatus(String message, int color) {
        this.statusMsg = new LiteralText(message);
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
        switch (s) {
            case ALL: return "全部";
            case LIMITED: return "仅在线";
            case NONE: return "隐藏";
            default: return "未知";
        }
    }
}
