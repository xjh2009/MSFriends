package dev.msf.friends.mixin;

import com.mojang.authlib.yggdrasil.FriendsService;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.screen.FriendsScreen;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.util.NotificationPrefs;
import dev.msf.friends.util.TurnPrefs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.SkinOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds MSF Friends options to the SkinOptionsScreen in MC 1.19.2.
 * 1.19.2 does not have OnlineOptionsScreen, so we use SkinOptionsScreen
 * as the closest equivalent for friend/presence settings.
 *
 * <p>Mirrors the 26.1.2 OnlineOptionsScreenMixin layout:
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
 */
@Mixin(SkinOptionsScreen.class)
public abstract class OnlineOptionsScreenMixin extends Screen {

    protected OnlineOptionsScreenMixin(Text title) {
        super(title);
    }

    // Friend feature buttons
    @Unique private @Nullable ButtonWidget msf$enableFriendsBtn;
    @Unique private @Nullable ButtonWidget msf$allowRequestsBtn;
    @Unique private @Nullable ButtonWidget msf$presenceSharingBtn;
    @Unique private @Nullable ButtonWidget msf$hiddenModeBtn;
    @Unique private @Nullable ButtonWidget msf$refreshBtn;
    @Unique private @Nullable ButtonWidget msf$openFriendsBtn;
    // Notification buttons
    @Unique private @Nullable ButtonWidget msf$notifyOnlineBtn;
    @Unique private @Nullable ButtonWidget msf$notifyStatusBtn;
    @Unique private @Nullable ButtonWidget msf$notifyInviteBtn;
    @Unique private @Nullable ButtonWidget msf$notifyJoinReqBtn;
    // TURN buttons
    @Unique private @Nullable ButtonWidget msf$turnModeBtn;
    @Unique private @Nullable ButtonWidget msf$iceModeBtn;
    // Status message
    @Unique private @Nullable Text msf$statusMsg;
    @Unique private int msf$statusColor = 0xFFFFFFFF;

    @Inject(method = "init", at = @At("TAIL"))
    private void msf$addFriendsOptions(CallbackInfo ci) {
        // SkinOptionsScreen in 1.19.2 has ~8 option rows + Done button
        // We place our buttons below the vanilla content
        // startY: after vanilla rows (height/6 + 24*4 ≈ height/6 + 96) + Done button gap
        int startY = this.height / 6 + 72 + 48;
        int centerX = this.width / 2;
        int row = 0;

        // --- 好友功能 header ---
        // (no header widget in 1.19.2, we just start the buttons)

        msf$enableFriendsBtn   = this.addDrawableChild(new ButtonWidget(centerX - 155, startY + row * 24, 150, 20, Text.empty(), b -> msf$toggleFriendFeature()));
        msf$allowRequestsBtn   = this.addDrawableChild(new ButtonWidget(centerX + 5,   startY + row * 24, 150, 20, Text.empty(), b -> msf$toggleAllowFriendRequests()));
        row++;
        msf$presenceSharingBtn = this.addDrawableChild(new ButtonWidget(centerX - 155, startY + row * 24, 150, 20, Text.empty(), b -> msf$cyclePresenceSharing()));
        msf$hiddenModeBtn      = this.addDrawableChild(new ButtonWidget(centerX + 5,   startY + row * 24, 150, 20, Text.empty(), b -> msf$toggleHiddenMode()));
        row++;
        msf$refreshBtn         = this.addDrawableChild(new ButtonWidget(centerX - 155, startY + row * 24, 150, 20, Text.literal("立即刷新"), b -> msf$forceRefresh()));
        msf$openFriendsBtn     = this.addDrawableChild(new ButtonWidget(centerX + 5,   startY + row * 24, 150, 20, Text.literal("打开好友界面"), b -> msf$openFriends()));
        row++;
        row++; // gap for "通知设置" section

        // --- 通知设置 ---
        msf$notifyOnlineBtn  = this.addDrawableChild(new ButtonWidget(centerX - 155, startY + row * 24, 150, 20, Text.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyOnline = !p.notifyOnline; p.save(); msf$syncFriendButtons(); }));
        msf$notifyStatusBtn  = this.addDrawableChild(new ButtonWidget(centerX + 5,   startY + row * 24, 150, 20, Text.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyStatus = !p.notifyStatus; p.save(); msf$syncFriendButtons(); }));
        row++;
        msf$notifyInviteBtn  = this.addDrawableChild(new ButtonWidget(centerX - 155, startY + row * 24, 150, 20, Text.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyInvite = !p.notifyInvite; p.save(); msf$syncFriendButtons(); }));
        msf$notifyJoinReqBtn = this.addDrawableChild(new ButtonWidget(centerX + 5,   startY + row * 24, 150, 20, Text.empty(), b -> { NotificationPrefs p = NotificationPrefs.get(); p.notifyJoinRequest = !p.notifyJoinRequest; p.save(); msf$syncFriendButtons(); }));
        row++;
        row++; // gap for "TURN加速设置" section

        // --- TURN加速设置 ---
        msf$turnModeBtn = this.addDrawableChild(new ButtonWidget(centerX - 155, startY + row * 24, 150, 20, Text.empty(), b -> msf$cycleTurnMode()));
        msf$iceModeBtn  = this.addDrawableChild(new ButtonWidget(centerX + 5,   startY + row * 24, 150, 20, Text.empty(), b -> msf$cycleIceMode()));

        msf$syncFriendButtons();
    }

    /**
     * Render section headers and status message.
     * 1.19.2 uses render(MatrixStack, int, int, float) instead of 26.1.2's extractRenderState.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void msf$renderOverlay(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int startY = this.height / 6 + 72 + 48;
        int centerX = this.width / 2;
        int headerColor = 0xE0E0E0;

        // Section headers
        drawCenteredText(matrices, this.textRenderer, Text.literal("— 好友功能 —"), centerX, startY - 12, headerColor);
        drawCenteredText(matrices, this.textRenderer, Text.literal("— 通知设置 —"), centerX, startY + 3 * 24 + 0, headerColor);
        drawCenteredText(matrices, this.textRenderer, Text.literal("— TURN加速设置 —"), centerX, startY + 6 * 24 + 0, headerColor);

        // Status message
        if (msf$statusMsg != null) {
            drawCenteredText(matrices, this.textRenderer, msf$statusMsg, centerX, 32, msf$statusColor);
        }
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

    @Unique private static String msf$onOff(boolean v) { return v ? "开启" : "关闭"; }

    @Unique private void msf$cycleIceMode() {
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
