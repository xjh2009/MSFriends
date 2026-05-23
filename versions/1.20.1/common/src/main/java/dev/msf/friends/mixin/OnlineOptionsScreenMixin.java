package dev.msf.friends.mixin;

import com.mojang.authlib.yggdrasil.FriendsService;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OnlineOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds MSF Friends options to the Online Options screen.
 *
 * In 1.20.1, OptionListWidget only accepts Option entries (no addHeader/addSmall
 * like 26.1.2). So we add our custom buttons as fixed-position widgets below
 * the option list. Section headers are rendered as centered text labels.
 * We also inject into render() to display status messages.
 */
@Mixin(OnlineOptionsScreen.class)
public abstract class OnlineOptionsScreenMixin extends GameOptionsScreen {

    protected OnlineOptionsScreenMixin(Screen lastScreen, GameOptions options, Text title) {
        super(lastScreen, options, title);
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

    // Track positions for section headers (rendered in render())
    @Unique private int msf$header1Y = -1;  // "好友功能"
    @Unique private int msf$header2Y = -1;  // "通知设置"
    @Unique private int msf$header3Y = -1;  // "TURN加速设置"

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsOptions(CallbackInfo ci) {
        // The option list in SimpleOptionsScreen occupies the top portion.
        // We place our first header below the vanilla options.
        int startY = this.height / 2 - 10;
        int col1 = this.width / 2 - 155;
        int col2 = this.width / 2 + 5;

        // Section 1: "好友功能"
        msf$header1Y = startY;
        startY += 14; // header height + small gap

        msf$enableFriendsBtn   = ButtonWidget.builder(Text.empty(), b -> msf$toggleFriendFeature())        .dimensions(col1, startY, 150, 20).build();
        msf$allowRequestsBtn   = ButtonWidget.builder(Text.empty(), b -> msf$toggleAllowFriendRequests())   .dimensions(col2, startY, 150, 20).build();
        startY += 24;
        msf$presenceSharingBtn = ButtonWidget.builder(Text.empty(), b -> msf$cyclePresenceSharing())        .dimensions(col1, startY, 150, 20).build();
        msf$hiddenModeBtn      = ButtonWidget.builder(Text.empty(), b -> msf$toggleHiddenMode())            .dimensions(col2, startY, 150, 20).build();
        startY += 24;
        msf$refreshBtn         = ButtonWidget.builder(Text.literal("立即刷新"),    b -> msf$forceRefresh())    .dimensions(col1, startY, 150, 20).build();
        msf$openFriendsBtn     = ButtonWidget.builder(Text.literal("打开好友界面"), b -> msf$openFriends())     .dimensions(col2, startY, 150, 20).build();

        // Section 2: "通知设置"
        startY += 28;
        msf$header2Y = startY;
        startY += 14;

        msf$notifyOnlineBtn  = ButtonWidget.builder(Text.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyOnline = !p.notifyOnline; p.save(); msf$syncFriendButtons(); }).dimensions(col1, startY, 150, 20).build();
        msf$notifyStatusBtn  = ButtonWidget.builder(Text.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyStatus = !p.notifyStatus; p.save(); msf$syncFriendButtons(); }).dimensions(col2, startY, 150, 20).build();
        startY += 24;
        msf$notifyInviteBtn  = ButtonWidget.builder(Text.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyInvite = !p.notifyInvite; p.save(); msf$syncFriendButtons(); }).dimensions(col1, startY, 150, 20).build();
        msf$notifyJoinReqBtn = ButtonWidget.builder(Text.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyJoinRequest = !p.notifyJoinRequest; p.save(); msf$syncFriendButtons(); }).dimensions(col2, startY, 150, 20).build();

        // Section 3: "TURN加速设置"
        startY += 28;
        msf$header3Y = startY;
        startY += 14;

        msf$turnModeBtn = ButtonWidget.builder(Text.empty(), b -> msf$cycleTurnMode()).dimensions(col1, startY, 150, 20).build();
        msf$iceModeBtn  = ButtonWidget.builder(Text.empty(), b -> msf$cycleIceMode()) .dimensions(col2, startY, 150, 20).build();

        this.addDrawableChild(msf$enableFriendsBtn);
        this.addDrawableChild(msf$allowRequestsBtn);
        this.addDrawableChild(msf$presenceSharingBtn);
        this.addDrawableChild(msf$hiddenModeBtn);
        this.addDrawableChild(msf$refreshBtn);
        this.addDrawableChild(msf$openFriendsBtn);
        this.addDrawableChild(msf$notifyOnlineBtn);
        this.addDrawableChild(msf$notifyStatusBtn);
        this.addDrawableChild(msf$notifyInviteBtn);
        this.addDrawableChild(msf$notifyJoinReqBtn);
        this.addDrawableChild(msf$turnModeBtn);
        this.addDrawableChild(msf$iceModeBtn);


        // Add a custom Drawable widget to render section headers and status message.
        // Since OnlineOptionsScreen doesn't override render() (inherited from SimpleOptionsScreen),
        // we can't @Inject into it directly. Using a Drawable child works around this.
        final int h1Y = msf$header1Y;
        final int h2Y = msf$header2Y;
        final int h3Y = msf$header3Y;
        this.addDrawable(new Drawable() {
            @Override
            public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
                if (h1Y >= 0) {
                    ctx.drawCenteredTextWithShadow(textRenderer, "好友功能", width / 2, h1Y, 0xFFE0E0E0);
                }
                if (h2Y >= 0) {
                    ctx.drawCenteredTextWithShadow(textRenderer, "通知设置", width / 2, h2Y, 0xFFE0E0E0);
                }
                if (h3Y >= 0) {
                    ctx.drawCenteredTextWithShadow(textRenderer, "TURN加速设置", width / 2, h3Y, 0xFFE0E0E0);
                }
                if (msf$statusMsg != null) {
                    ctx.drawCenteredTextWithShadow(textRenderer, msf$statusMsg, width / 2, 32, msf$statusColor);
                }
            }
        });
    }

    @Unique
    private void msf$syncFriendButtons() {
        var client = MsfFriendsBoot.get();
        PlayerSocialManager social = client != null ? client.social() : null;
        MinecraftBridge bridge     = client != null ? client.bridge() : null;
        boolean ready = social != null && bridge != null;

        if (msf$enableFriendsBtn != null) {
            msf$enableFriendsBtn.setMessage(Text.literal("启用好友功能: "
                    + (social != null ? msf$onOff(social.isFriendListEnabled()) : "未就绪")));
            msf$enableFriendsBtn.active = ready;
        }
        if (msf$allowRequestsBtn != null) {
            msf$allowRequestsBtn.setMessage(Text.literal("好友请求: "
                    + (social != null ? (social.isAllowFriendRequests() ? "允许" : "拒绝") : "未就绪")));
            msf$allowRequestsBtn.active = ready;
        }
        if (msf$presenceSharingBtn != null) {
            msf$presenceSharingBtn.setMessage(Text.literal("在线状态共享: "
                    + (bridge != null ? msf$sharingLabel(bridge.presenceSharing()) : "未就绪")));
            msf$presenceSharingBtn.active = ready;
        }
        if (msf$hiddenModeBtn != null) {
            msf$hiddenModeBtn.setMessage(Text.literal("隐身模式: "
                    + (bridge != null ? msf$onOff(bridge.hiddenMode()) : "未就绪")));
            msf$hiddenModeBtn.active = ready;
        }
        if (msf$refreshBtn    != null) msf$refreshBtn.active    = social != null;
        if (msf$openFriendsBtn != null) msf$openFriendsBtn.active = this.client != null;

        NotificationPrefs prefs = NotificationPrefs.get();
        if (msf$notifyOnlineBtn  != null) msf$notifyOnlineBtn .setMessage(Text.literal("好友上线通知: "   + msf$onOff(prefs.notifyOnline)));
        if (msf$notifyStatusBtn  != null) msf$notifyStatusBtn .setMessage(Text.literal("游戏状态通知: "   + msf$onOff(prefs.notifyStatus)));
        if (msf$notifyInviteBtn  != null) msf$notifyInviteBtn .setMessage(Text.literal("邀请通知: "       + msf$onOff(prefs.notifyInvite)));
        if (msf$notifyJoinReqBtn != null) msf$notifyJoinReqBtn.setMessage(Text.literal("加入申请通知: " + msf$onOff(prefs.notifyJoinRequest)));
        if (msf$turnModeBtn != null) msf$turnModeBtn.setMessage(Text.literal("国内TURN加速: " + TurnPrefs.get().turnMode.displayName()));
        if (msf$iceModeBtn  != null) msf$iceModeBtn .setMessage(Text.literal("连接模式: "    + TurnPrefs.get().iceMode.displayName()));
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
        if (this.client != null) this.client.setScreen(new FriendsScreen((Screen) (Object) this));
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
        msf$statusMsg   = Text.literal(message);
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
