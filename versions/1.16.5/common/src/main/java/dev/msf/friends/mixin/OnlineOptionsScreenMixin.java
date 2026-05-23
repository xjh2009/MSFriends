package dev.msf.friends.mixin;

import com.mojang.authlib.yggdrasil.FriendsService;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into OptionsScreen (1.16.5 has no OnlineOptionsScreen).
 *
 * <p>Mirrors 26.1.2's OnlineOptionsScreenMixin by adding all friend-related
 * option buttons directly into the Options screen. Since 1.16.5 does not have
 * an OnlineOptionsScreen, we inject a new section at the bottom of the Options
 * screen with the same layout:
 *
 * <pre>
 *   [启用好友功能] [好友请求]
 *   [在线状态共享] [隐身模式]
 *   [立即刷新]     [打开好友界面]
 *   [上线通知]     [状态变化通知]
 *   [邀请通知]     [加入请求通知]
 *   [TURN模式]     [ICE模式]
 * </pre>
 */
@Mixin(OptionsScreen.class)
public abstract class OnlineOptionsScreenMixin extends Screen {

    protected OnlineOptionsScreenMixin(Text title) {
        super(title);
    }

    @Unique private @Nullable ButtonWidget msf$enableFriendsBtn;
    @Unique private @Nullable ButtonWidget msf$allowRequestsBtn;
    @Unique private @Nullable ButtonWidget msf$presenceSharingBtn;
    @Unique private @Nullable ButtonWidget msf$hiddenModeBtn;
    @Unique private @Nullable ButtonWidget msf$refreshBtn;
    @Unique private @Nullable ButtonWidget msf$openFriendsBtn;
    @Unique private @Nullable ButtonWidget msf$notifyOnlineBtn;
    @Unique private @Nullable ButtonWidget msf$notifyStatusBtn;
    @Unique private @Nullable ButtonWidget msf$notifyInviteBtn;
    @Unique private @Nullable ButtonWidget msf$notifyJoinReqBtn;
    @Unique private @Nullable ButtonWidget msf$turnModeBtn;
    @Unique private @Nullable ButtonWidget msf$iceModeBtn;
    @Unique private @Nullable Text msf$statusMsg;
    @Unique private int msf$statusColor = 0xFFFFFFFF;

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsOptions(CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        int centerX = this.width / 2;
        int leftX = centerX - 155;
        int rightX = centerX + 5;

        // Start below the vanilla Done button area
        // 1.16.5 OptionsScreen: buttons end around height/6 + 168
        // We add our section below with some spacing
        int y = this.height / 6 + 168 + 8;

        // Section: 好友功能
        msf$enableFriendsBtn   = this.addButton(new ButtonWidget(leftX, y, 150, 20, LiteralText.EMPTY, b -> msf$toggleFriendFeature()));
        msf$allowRequestsBtn   = this.addButton(new ButtonWidget(rightX, y, 150, 20, LiteralText.EMPTY, b -> msf$toggleAllowFriendRequests()));

        y += 24;
        msf$presenceSharingBtn = this.addButton(new ButtonWidget(leftX, y, 150, 20, LiteralText.EMPTY, b -> msf$cyclePresenceSharing()));
        msf$hiddenModeBtn      = this.addButton(new ButtonWidget(rightX, y, 150, 20, LiteralText.EMPTY, b -> msf$toggleHiddenMode()));

        y += 24;
        msf$refreshBtn         = this.addButton(new ButtonWidget(leftX, y, 150, 20, new LiteralText("立即刷新"), b -> msf$forceRefresh()));
        msf$openFriendsBtn     = this.addButton(new ButtonWidget(rightX, y, 150, 20, new LiteralText("打开好友界面"), b -> msf$openFriends()));

        // Section: 通知设置
        y += 28;
        msf$notifyOnlineBtn   = this.addButton(new ButtonWidget(leftX, y, 150, 20, LiteralText.EMPTY, b -> {
            NotificationPrefs p = NotificationPrefs.get(); p.notifyOnline = !p.notifyOnline; p.save(); msf$syncFriendButtons();
        }));
        msf$notifyStatusBtn   = this.addButton(new ButtonWidget(rightX, y, 150, 20, LiteralText.EMPTY, b -> {
            NotificationPrefs p = NotificationPrefs.get(); p.notifyStatus = !p.notifyStatus; p.save(); msf$syncFriendButtons();
        }));

        y += 24;
        msf$notifyInviteBtn   = this.addButton(new ButtonWidget(leftX, y, 150, 20, LiteralText.EMPTY, b -> {
            NotificationPrefs p = NotificationPrefs.get(); p.notifyInvite = !p.notifyInvite; p.save(); msf$syncFriendButtons();
        }));
        msf$notifyJoinReqBtn  = this.addButton(new ButtonWidget(rightX, y, 150, 20, LiteralText.EMPTY, b -> {
            NotificationPrefs p = NotificationPrefs.get(); p.notifyJoinRequest = !p.notifyJoinRequest; p.save(); msf$syncFriendButtons();
        }));

        // Section: TURN加速设置
        y += 28;
        msf$turnModeBtn       = this.addButton(new ButtonWidget(leftX, y, 150, 20, LiteralText.EMPTY, b -> msf$cycleTurnMode()));
        msf$iceModeBtn        = this.addButton(new ButtonWidget(rightX, y, 150, 20, LiteralText.EMPTY, b -> msf$cycleIceMode()));

        msf$syncFriendButtons();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void msf$renderStatusAndHeaders(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (MsfFriendsBoot.get() == null) return;

        int centerX = this.width / 2;

        // Section headers
        int y = this.height / 6 + 168 + 8 - 12;
        drawCenteredText(matrices, this.textRenderer, new LiteralText("—— 好友功能 ——"), centerX, y, 0xFFFFAA00);

        if (msf$notifyOnlineBtn != null) {
            drawCenteredText(matrices, this.textRenderer, new LiteralText("—— 通知设置 ——"), centerX, msf$notifyOnlineBtn.y - 14, 0xFFFFAA00);
        }
        if (msf$turnModeBtn != null) {
            drawCenteredText(matrices, this.textRenderer, new LiteralText("—— TURN加速设置 ——"), centerX, msf$turnModeBtn.y - 14, 0xFFFFAA00);
        }

        // Status message
        if (msf$statusMsg != null) {
            drawCenteredText(matrices, this.textRenderer, msf$statusMsg, centerX, this.height / 6 + 168 + 8 - 26, msf$statusColor);
        }
    }

    @Unique
    private void msf$syncFriendButtons() {
        var client = MsfFriendsBoot.get();
        PlayerSocialManager social = client != null ? client.social() : null;
        MinecraftBridge bridge     = client != null ? client.bridge() : null;
        boolean ready = social != null && bridge != null;

        if (msf$enableFriendsBtn != null) {
            msf$enableFriendsBtn.setMessage(new LiteralText("启用好友功能: "
                    + (social != null ? msf$onOff(social.isFriendListEnabled()) : "未就绪")));
            msf$enableFriendsBtn.active = ready;
        }
        if (msf$allowRequestsBtn != null) {
            msf$allowRequestsBtn.setMessage(new LiteralText("好友请求: "
                    + (social != null ? (social.isAllowFriendRequests() ? "允许" : "拒绝") : "未就绪")));
            msf$allowRequestsBtn.active = ready;
        }
        if (msf$presenceSharingBtn != null) {
            msf$presenceSharingBtn.setMessage(new LiteralText("在线状态共享: "
                    + (bridge != null ? msf$sharingLabel(bridge.presenceSharing()) : "未就绪")));
            msf$presenceSharingBtn.active = ready;
        }
        if (msf$hiddenModeBtn != null) {
            msf$hiddenModeBtn.setMessage(new LiteralText("隐身模式: "
                    + (bridge != null ? msf$onOff(bridge.hiddenMode()) : "未就绪")));
            msf$hiddenModeBtn.active = ready;
        }
        if (msf$refreshBtn     != null) msf$refreshBtn.active     = social != null;
        if (msf$openFriendsBtn != null) msf$openFriendsBtn.active = this.client != null;

        NotificationPrefs prefs = NotificationPrefs.get();
        if (msf$notifyOnlineBtn  != null) msf$notifyOnlineBtn .setMessage(new LiteralText("好友上线通知: "   + msf$onOff(prefs.notifyOnline)));
        if (msf$notifyStatusBtn  != null) msf$notifyStatusBtn .setMessage(new LiteralText("游戏状态通知: "   + msf$onOff(prefs.notifyStatus)));
        if (msf$notifyInviteBtn  != null) msf$notifyInviteBtn .setMessage(new LiteralText("邀请通知: "       + msf$onOff(prefs.notifyInvite)));
        if (msf$notifyJoinReqBtn != null) msf$notifyJoinReqBtn.setMessage(new LiteralText("加入申请通知: " + msf$onOff(prefs.notifyJoinRequest)));
        if (msf$turnModeBtn      != null) msf$turnModeBtn.setMessage(new LiteralText("国内TURN加速: " + TurnPrefs.get().turnMode.displayName()));
        if (msf$iceModeBtn       != null) msf$iceModeBtn .setMessage(new LiteralText("连接模式: "    + TurnPrefs.get().iceMode.displayName()));
    }

    @Unique
    private String msf$onOff(boolean val) { return val ? "开启" : "关闭"; }

    @Unique
    private String msf$sharingLabel(MinecraftBridge.PresenceSharing sharing) {
        return switch (sharing) {
            case ALL     -> "全部";
            case LIMITED -> "仅在线";
            case NONE    -> "隐藏";
        };
    }

    @Unique
    private void msf$toggleFriendFeature() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) return;
        msf$saveFriendSettings(!client.social().isFriendListEnabled(), client.social().isAllowFriendRequests());
    }

    @Unique
    private void msf$toggleAllowFriendRequests() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) return;
        msf$saveFriendSettings(client.social().isFriendListEnabled(), !client.social().isAllowFriendRequests());
    }

    @Unique
    private void msf$saveFriendSettings(boolean fle, boolean afr) {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.client == null) {
            msf$setStatus("好友服务未就绪", 0xFFFF8080); return;
        }
        msf$setStatus("正在保存设置...", 0xFFE0E0E0);
        client.social().updateFriendSettings(fle, afr).whenComplete((result, error) ->
                this.client.execute(() -> {
                    if (error != null) {
                        msf$setStatus("保存失败", 0xFFFF8080);
                    } else {
                        msf$applyResult("设置已保存", result);
                    }
                    msf$syncFriendButtons();
                }));
    }

    @Unique
    private void msf$applyResult(String successMessage, FriendsService.ResultCode result) {
        switch (result) {
            case SUCCESS -> msf$setStatus(successMessage, 0xFF55FF55);
            case TOO_MANY_REQUESTS -> msf$setStatus("操作过于频繁", 0xFFFFAA00);
            case UNKNOWN_PROFILE -> msf$setStatus("找不到该玩家", 0xFFFF8080);
            case FORBIDDEN -> msf$setStatus("服务拒绝了本次操作", 0xFFFF8080);
            case SERVICE_NOT_AVAILABLE, TEMPORARY_UNAVAILABLE -> msf$setStatus("好友服务不可用", 0xFFFF8080);
            case CONNECTION_ISSUE -> msf$setStatus("网络连接异常", 0xFFFF8080);
            case UPGRADE_NEEDED -> msf$setStatus("版本过低", 0xFFFF8080);
            case GENERIC_ERROR, ERROR -> msf$setStatus("操作失败", 0xFFFF8080);
        }
    }

    @Unique
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
        msf$syncFriendButtons();
    }

    @Unique
    private void msf$toggleHiddenMode() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.bridge() == null || client.social() == null) return;
        boolean next = !client.bridge().hiddenMode();
        client.social().getPresenceHandler().setHiddenMode(next);
        msf$setStatus(next ? "隐身模式已开启" : "隐身模式已关闭", 0xFF55FF55);
        msf$syncFriendButtons();
    }

    @Unique
    private void msf$forceRefresh() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.client == null) return;
        msf$setStatus("正在刷新...", 0xFFE0E0E0);
        client.social().getPresenceHandler().tryUpdatePresence();
        client.social().getPresenceHandler().tick();
        client.social().getRemoteFriendListUpdateHandler().forceUpdate().whenComplete((ignored, error) ->
                this.client.execute(() -> {
                    msf$setStatus(error != null ? "刷新失败" : "已刷新", error != null ? 0xFFFF8080 : 0xFF55FF55);
                    msf$syncFriendButtons();
                }));
    }

    @Unique
    private void msf$openFriends() {
        if (this.client != null) this.client.openScreen(new FriendsScreen((Screen) (Object) this));
    }

    @Unique
    private void msf$cycleTurnMode() {
        TurnPrefs prefs = TurnPrefs.get();
        prefs.turnMode = prefs.turnMode.next();
        prefs.save();
        msf$syncFriendButtons();
    }

    @Unique
    private void msf$cycleIceMode() {
        TurnPrefs prefs = TurnPrefs.get();
        prefs.iceMode = prefs.iceMode.next();
        prefs.save();
        msf$syncFriendButtons();
    }

    @Unique
    private void msf$setStatus(String message, int color) {
        this.msf$statusMsg = new LiteralText(message);
        this.msf$statusColor = color;
    }
}
