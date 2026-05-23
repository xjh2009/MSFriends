package dev.msf.friends.mixin;

import com.mojang.authlib.yggdrasil.FriendsService;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds MSF Friends options to the OnlineOptionsScreen in MC 1.18.2.
 * 1.18.2 has OnlineOptionsScreen (class_6777) which extends OptionsSubScreen.
 *
 * <p>Layout mirrors other versions:
 * <pre>
 *   --- 好友功能 ---
 *   [启用好友功能]  [好友请求]
 *   [在线状态共享]  [隐身模式]
 *   [立即刷新]      [打开好友界面]
 *   --- 通知设置 ---
 *   [好友上线通知]  [游戏状态通知]
 *   [邀请通知]      [加入申请通知]
 *   --- TURN加速设置 ---
 *   [国内TURN加速]  [连接模式]
 * </pre>
 *
 * <p>1.18.2 API: Button(x,y,w,h,Component,pressAction), PoseStack for render,
 * TextComponent for literal text.
 */
@Mixin(OnlineOptionsScreen.class)
public abstract class OnlineOptionsScreenMixin extends OptionsSubScreen {

    protected OnlineOptionsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    // Friend feature buttons
    @Unique private @Nullable Button msf$enableFriendsBtn;
    @Unique private @Nullable Button msf$allowRequestsBtn;
    @Unique private @Nullable Button msf$presenceSharingBtn;
    @Unique private @Nullable Button msf$hiddenModeBtn;
    @Unique private @Nullable Button msf$refreshBtn;
    @Unique private @Nullable Button msf$openFriendsBtn;
    // Notification buttons
    @Unique private @Nullable Button msf$notifyOnlineBtn;
    @Unique private @Nullable Button msf$notifyStatusBtn;
    @Unique private @Nullable Button msf$notifyInviteBtn;
    @Unique private @Nullable Button msf$notifyJoinReqBtn;
    // TURN buttons
    @Unique private @Nullable Button msf$turnModeBtn;
    @Unique private @Nullable Button msf$iceModeBtn;
    // Status message
    @Unique private @Nullable Component msf$statusMsg;
    @Unique private int msf$statusColor = 0xFFFFFFFF;

    // Track positions for section headers (rendered via addRenderableOnly)
    @Unique private int msf$header1Y = -1;  // "好友功能"
    @Unique private int msf$header2Y = -1;  // "通知设置"
    @Unique private int msf$header3Y = -1;  // "TURN加速设置"
    /**
     * Inject at TAIL of createFooter() — this method is declared directly in
     * OnlineOptionsScreen in 1.18.2, so the refmap will correctly resolve it.
     * (Injecting into "init" fails refmap generation because init is inherited
     * from the parent class.)
     */
    @Inject(method = "createFooter", at = @At("TAIL"))
    private void msf$addFriendsOptions(CallbackInfo ci) {
        // The option list in 1.18.2 OnlineOptionsScreen occupies the top portion.
        // We place our first header below the vanilla options.
        int startY = this.height / 2 - 10;
        int col1 = this.width / 2 - 155;
        int col2 = this.width / 2 + 5;

        // Section 1: "好友功能"
        msf$header1Y = startY;
        startY += 14; // header height + small gap

        msf$enableFriendsBtn   = this.addRenderableWidget(new Button(col1, startY, 150, 20, TextComponent.EMPTY, b -> msf$toggleFriendFeature()));
        msf$allowRequestsBtn   = this.addRenderableWidget(new Button(col2, startY, 150, 20, TextComponent.EMPTY, b -> msf$toggleAllowFriendRequests()));
        startY += 24;
        msf$presenceSharingBtn = this.addRenderableWidget(new Button(col1, startY, 150, 20, TextComponent.EMPTY, b -> msf$cyclePresenceSharing()));
        msf$hiddenModeBtn      = this.addRenderableWidget(new Button(col2, startY, 150, 20, TextComponent.EMPTY, b -> msf$toggleHiddenMode()));
        startY += 24;
        msf$refreshBtn         = this.addRenderableWidget(new Button(col1, startY, 150, 20, new TextComponent("立即刷新"), b -> msf$forceRefresh()));
        msf$openFriendsBtn     = this.addRenderableWidget(new Button(col2, startY, 150, 20, new TextComponent("打开好友界面"), b -> msf$openFriends()));

        // Section 2: "通知设置"
        startY += 28;
        msf$header2Y = startY;
        startY += 14;

        msf$notifyOnlineBtn  = this.addRenderableWidget(new Button(col1, startY, 150, 20, TextComponent.EMPTY, b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyOnline = !p.notifyOnline; p.save(); msf$syncFriendButtons(); }));
        msf$notifyStatusBtn  = this.addRenderableWidget(new Button(col2, startY, 150, 20, TextComponent.EMPTY, b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyStatus = !p.notifyStatus; p.save(); msf$syncFriendButtons(); }));
        startY += 24;
        msf$notifyInviteBtn  = this.addRenderableWidget(new Button(col1, startY, 150, 20, TextComponent.EMPTY, b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyInvite = !p.notifyInvite; p.save(); msf$syncFriendButtons(); }));
        msf$notifyJoinReqBtn = this.addRenderableWidget(new Button(col2, startY, 150, 20, TextComponent.EMPTY, b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyJoinRequest = !p.notifyJoinRequest; p.save(); msf$syncFriendButtons(); }));

        // Section 3: "TURN加速设置"
        startY += 28;
        msf$header3Y = startY;
        startY += 14;

        msf$turnModeBtn = this.addRenderableWidget(new Button(col1, startY, 150, 20, TextComponent.EMPTY, b -> msf$cycleTurnMode()));
        msf$iceModeBtn  = this.addRenderableWidget(new Button(col2, startY, 150, 20, TextComponent.EMPTY, b -> msf$cycleIceMode()));

        // Section header labels + status message rendered via addRenderableOnly
        final int headerColor = 0xE0E0E0;
        final int h1Y = msf$header1Y;
        final int h2Y = msf$header2Y;
        final int h3Y = msf$header3Y;
        this.addRenderableOnly((matrices, mx, my, delta) -> {
            drawCenteredString(matrices, this.font, "— 好友功能 —", this.width / 2, h1Y, headerColor);
            drawCenteredString(matrices, this.font, "— 通知设置 —", this.width / 2, h2Y, headerColor);
            drawCenteredString(matrices, this.font, "— TURN加速设置 —", this.width / 2, h3Y, headerColor);
            if (msf$statusMsg != null) {
                drawCenteredString(matrices, this.font, msf$statusMsg, this.width / 2, 32, msf$statusColor);
            }
        });

        msf$syncFriendButtons();
    }

    @Unique
    private void msf$syncFriendButtons() {
        var client = MsfFriendsBoot.get();
        PlayerSocialManager social = client != null ? client.social() : null;
        MinecraftBridge bridge     = client != null ? client.bridge() : null;
        boolean ready = social != null && bridge != null;

        if (msf$enableFriendsBtn != null) {
            msf$enableFriendsBtn.setMessage(new TextComponent("启用好友功能: "
                    + (social != null ? msf$onOff(social.isFriendListEnabled()) : "未就绪")));
            msf$enableFriendsBtn.active = ready;
        }
        if (msf$allowRequestsBtn != null) {
            msf$allowRequestsBtn.setMessage(new TextComponent("好友请求: "
                    + (social != null ? (social.isAllowFriendRequests() ? "允许" : "拒绝") : "未就绪")));
            msf$allowRequestsBtn.active = ready;
        }
        if (msf$presenceSharingBtn != null) {
            msf$presenceSharingBtn.setMessage(new TextComponent("在线状态共享: "
                    + (bridge != null ? msf$sharingLabel(bridge.presenceSharing()) : "未就绪")));
            msf$presenceSharingBtn.active = ready;
        }
        if (msf$hiddenModeBtn != null) {
            msf$hiddenModeBtn.setMessage(new TextComponent("隐身模式: "
                    + (bridge != null ? msf$onOff(bridge.hiddenMode()) : "未就绪")));
            msf$hiddenModeBtn.active = ready;
        }
        if (msf$refreshBtn    != null) msf$refreshBtn.active    = social != null;
        if (msf$openFriendsBtn != null) msf$openFriendsBtn.active = this.minecraft != null;

        NotificationPrefs prefs = NotificationPrefs.get();
        if (msf$notifyOnlineBtn  != null) msf$notifyOnlineBtn .setMessage(new TextComponent("好友上线通知: "   + msf$onOff(prefs.notifyOnline)));
        if (msf$notifyStatusBtn  != null) msf$notifyStatusBtn .setMessage(new TextComponent("游戏状态通知: "   + msf$onOff(prefs.notifyStatus)));
        if (msf$notifyInviteBtn  != null) msf$notifyInviteBtn .setMessage(new TextComponent("邀请通知: "       + msf$onOff(prefs.notifyInvite)));
        if (msf$notifyJoinReqBtn != null) msf$notifyJoinReqBtn.setMessage(new TextComponent("加入申请通知: " + msf$onOff(prefs.notifyJoinRequest)));
        if (msf$turnModeBtn != null) msf$turnModeBtn.setMessage(new TextComponent("国内TURN加速: " + TurnPrefs.get().turnMode.displayName()));
        if (msf$iceModeBtn  != null) msf$iceModeBtn .setMessage(new TextComponent("连接模式: "    + TurnPrefs.get().iceMode.displayName()));
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
        if (client == null || client.social() == null || this.minecraft == null) {
            msf$setStatus("好友服务未就绪", 0xFFFF8080); return;
        }
        msf$setStatus("正在保存设置...", 0xFFE0E0E0);
        client.social().updateFriendSettings(fle, afr).whenComplete((result, error) ->
                this.minecraft.execute(() -> {
                    if (error != null) {
                        msf$setStatus("保存失败", 0xFFFF8080);
                    } else {
                        msf$applyResult("设置已保存", result);
                    }
                    msf$syncFriendButtons();
                }));
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
        if (client == null || client.social() == null || this.minecraft == null) return;
        msf$setStatus("正在刷新...", 0xFFE0E0E0);
        client.social().getPresenceHandler().tryUpdatePresence();
        client.social().getPresenceHandler().tick();
        client.social().getRemoteFriendListUpdateHandler().forceUpdate().whenComplete((ignored, error) ->
                this.minecraft.execute(() -> {
                    msf$setStatus(error != null ? "刷新失败" : "已刷新", error != null ? 0xFFFF8080 : 0xFF55FF55);
                    msf$syncFriendButtons();
                }));
    }

    @Unique
    private void msf$openFriends() {
        if (this.minecraft != null) this.minecraft.setScreen(new FriendsScreen((Screen) (Object) this));
    }

    @Unique
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

    @Unique
    private void msf$setStatus(String message, int color) {
        msf$statusMsg   = new TextComponent(message);
        msf$statusColor = color;
    }

    @Unique private static String msf$onOff(boolean v) { return v ? "开启" : "关闭"; }

    @Unique
    private void msf$cycleIceMode() {
        TurnPrefs prefs = TurnPrefs.get();
        prefs.iceMode = prefs.iceMode.next();
        prefs.save();
        msf$syncFriendButtons();
    }

    @Unique
    private void msf$cycleTurnMode() {
        TurnPrefs prefs = TurnPrefs.get();
        prefs.turnMode = prefs.turnMode.next();
        prefs.save();
        msf$syncFriendButtons();
    }

    @Unique
    private static String msf$sharingLabel(MinecraftBridge.PresenceSharing s) {
        return switch (s) {
            case ALL     -> "全部";
            case LIMITED -> "仅在线";
            case NONE    -> "隐藏";
        };
    }
}
