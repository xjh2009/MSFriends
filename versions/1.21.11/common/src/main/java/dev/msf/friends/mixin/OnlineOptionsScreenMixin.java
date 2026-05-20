package dev.msf.friends.mixin;

import com.mojang.authlib.yggdrasil.FriendsService;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OnlineOptionsScreen.class)
public abstract class OnlineOptionsScreenMixin extends OptionsSubScreen {

    protected OnlineOptionsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Unique private @Nullable Button msf$enableFriendsBtn;
    @Unique private @Nullable Button msf$allowRequestsBtn;
    @Unique private @Nullable Button msf$presenceSharingBtn;
    @Unique private @Nullable Button msf$hiddenModeBtn;
    @Unique private @Nullable Button msf$refreshBtn;
    @Unique private @Nullable Button msf$openFriendsBtn;
    @Unique private @Nullable Button msf$notifyOnlineBtn;
    @Unique private @Nullable Button msf$notifyStatusBtn;
    @Unique private @Nullable Button msf$notifyInviteBtn;
    @Unique private @Nullable Button msf$notifyJoinReqBtn;
    @Unique private @Nullable Button msf$turnModeBtn;
    @Unique private @Nullable Button msf$iceModeBtn;
    @Unique private @Nullable Component msf$statusMsg;
    @Unique private int msf$statusColor = 0xFFFFFFFF;

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void msf$addFriendsOptions(CallbackInfo ci) {
        this.list.addHeader(Component.literal("好友功能"));

        msf$enableFriendsBtn   = Button.builder(Component.empty(), b -> msf$toggleFriendFeature())        .bounds(0, 0, 150, 20).build();
        msf$allowRequestsBtn   = Button.builder(Component.empty(), b -> msf$toggleAllowFriendRequests())   .bounds(0, 0, 150, 20).build();
        msf$presenceSharingBtn = Button.builder(Component.empty(), b -> msf$cyclePresenceSharing())        .bounds(0, 0, 150, 20).build();
        msf$hiddenModeBtn      = Button.builder(Component.empty(), b -> msf$toggleHiddenMode())            .bounds(0, 0, 150, 20).build();
        msf$refreshBtn         = Button.builder(Component.literal("立即刷新"),    b -> msf$forceRefresh())    .bounds(0, 0, 150, 20).build();
        msf$openFriendsBtn     = Button.builder(Component.literal("打开好友界面"), b -> msf$openFriends())     .bounds(0, 0, 150, 20).build();

        this.list.addSmall(msf$enableFriendsBtn,   msf$allowRequestsBtn);
        this.list.addSmall(msf$presenceSharingBtn, msf$hiddenModeBtn);
        this.list.addSmall(msf$refreshBtn,         msf$openFriendsBtn);

        this.list.addHeader(Component.literal("通知设置"));
        msf$notifyOnlineBtn  = Button.builder(Component.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyOnline = !p.notifyOnline; p.save(); msf$syncFriendButtons(); }).bounds(0, 0, 150, 20).build();
        msf$notifyStatusBtn  = Button.builder(Component.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyStatus = !p.notifyStatus; p.save(); msf$syncFriendButtons(); }).bounds(0, 0, 150, 20).build();
        msf$notifyInviteBtn  = Button.builder(Component.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyInvite = !p.notifyInvite; p.save(); msf$syncFriendButtons(); }).bounds(0, 0, 150, 20).build();
        msf$notifyJoinReqBtn = Button.builder(Component.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyJoinRequest = !p.notifyJoinRequest; p.save(); msf$syncFriendButtons(); }).bounds(0, 0, 150, 20).build();
        this.list.addSmall(msf$notifyOnlineBtn,  msf$notifyStatusBtn);
        this.list.addSmall(msf$notifyInviteBtn,  msf$notifyJoinReqBtn);

        this.list.addHeader(Component.literal("TURN加速设置"));
        msf$turnModeBtn = Button.builder(Component.empty(), b -> msf$cycleTurnMode())
                .bounds(0, 0, 150, 20).build();
        msf$iceModeBtn = Button.builder(Component.empty(), b -> msf$cycleIceMode())
                .bounds(0, 0, 150, 20).build();
        this.list.addSmall(msf$turnModeBtn, msf$iceModeBtn);

        msf$syncFriendButtons();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void msf$renderStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (msf$statusMsg != null) {
            graphics.centeredText(this.font, msf$statusMsg, this.width / 2, 32, msf$statusColor);
        }
    }

    @Unique
    private void msf$syncFriendButtons() {
        var client = MsfFriendsBoot.get();
        PlayerSocialManager social = client != null ? client.social() : null;
        MinecraftBridge bridge     = client != null ? client.bridge() : null;
        boolean ready = social != null && bridge != null;

        if (msf$enableFriendsBtn != null) {
            msf$enableFriendsBtn.setMessage(Component.literal("启用好友功能: "
                    + (social != null ? msf$onOff(social.isFriendListEnabled()) : "未就绪")));
            msf$enableFriendsBtn.active = ready;
        }
        if (msf$allowRequestsBtn != null) {
            msf$allowRequestsBtn.setMessage(Component.literal("好友请求: "
                    + (social != null ? (social.isAllowFriendRequests() ? "允许" : "拒绝") : "未就绪")));
            msf$allowRequestsBtn.active = ready;
        }
        if (msf$presenceSharingBtn != null) {
            msf$presenceSharingBtn.setMessage(Component.literal("在线状态共享: "
                    + (bridge != null ? msf$sharingLabel(bridge.presenceSharing()) : "未就绪")));
            msf$presenceSharingBtn.active = ready;
        }
        if (msf$hiddenModeBtn != null) {
            msf$hiddenModeBtn.setMessage(Component.literal("隐身模式: "
                    + (bridge != null ? msf$onOff(bridge.hiddenMode()) : "未就绪")));
            msf$hiddenModeBtn.active = ready;
        }
        if (msf$refreshBtn    != null) msf$refreshBtn.active    = social != null;
        if (msf$openFriendsBtn != null) msf$openFriendsBtn.active = this.minecraft != null;

        NotificationPrefs prefs = NotificationPrefs.get();
        if (msf$notifyOnlineBtn  != null) msf$notifyOnlineBtn .setMessage(Component.literal("好友上线通知: "   + msf$onOff(prefs.notifyOnline)));
        if (msf$notifyStatusBtn  != null) msf$notifyStatusBtn .setMessage(Component.literal("游戏状态通知: "   + msf$onOff(prefs.notifyStatus)));
        if (msf$notifyInviteBtn  != null) msf$notifyInviteBtn .setMessage(Component.literal("邀请通知: "       + msf$onOff(prefs.notifyInvite)));
        if (msf$notifyJoinReqBtn != null) msf$notifyJoinReqBtn.setMessage(Component.literal("加入申请通知: " + msf$onOff(prefs.notifyJoinRequest)));
        if (msf$turnModeBtn != null) msf$turnModeBtn.setMessage(Component.literal("国内TURN加速: " + TurnPrefs.get().turnMode.displayName()));
        if (msf$iceModeBtn  != null) msf$iceModeBtn .setMessage(Component.literal("连接模式: "    + TurnPrefs.get().iceMode.displayName()));
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
        msf$statusMsg   = Component.literal(message);
        msf$statusColor = color;
    }

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

    @Unique private static String msf$onOff(boolean v) { return v ? "开启" : "关闭"; }

    @Unique
    private static String msf$sharingLabel(MinecraftBridge.PresenceSharing s) {
        return switch (s) {
            case ALL     -> "全部";
            case LIMITED -> "仅在线";
            case NONE    -> "隐藏";
        };
    }
}
