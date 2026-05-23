package dev.msf.friends.screen;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.FriendJoinHandler;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.PresenceHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static net.minecraft.client.gui.DrawableHelper.*;

/**
 * Native friends screen for MC 1.19.2 Yarn.
 * Uses older ButtonWidget constructor API (not builder pattern).
 * No SpriteIconButton — uses plain text buttons instead.
 */
public class FriendsScreen extends Screen {
    private static final Text TITLE = tr("screen.msf_friends.friends.title");
    private static final Identifier BACKGROUND_TEXTURE = new Identifier("minecraft", "textures/gui/social_interactions.png");
    // 1.19.2 does not have textures/gui/icon/search.png, so we skip the search icon
    private static final Text SEARCH_HINT = Text.translatable("screen.msf_friends.friends.search_hint");
    private static final Text TAB_FRIENDS = tr("screen.msf_friends.friends.tab.friends");
    private static final Text TAB_PENDING = tr("screen.msf_friends.friends.tab.pending");
    private static final Text BUTTON_ADD = tr("screen.msf_friends.friends.button.add");
    private static final Text EMPTY_FRIENDS = Text.translatable("screen.msf_friends.friends.empty.friends");
    private static final Text EMPTY_PENDING = Text.translatable("screen.msf_friends.friends.empty.pending");
    private static final Text EMPTY_FILTER = Text.translatable("screen.msf_friends.friends.empty.filter");
    private static final int BG_WIDTH = 236;
    private static final int ITEM_HEIGHT = 36;
    static final int SKIN_SIZE = 24;
    static final int PLAYERNAME_COLOR = 0xFFFFFFFF;

    private final Screen parent;
    private Page page = Page.FRIENDS;
    private @Nullable FriendsPlayerList playerList;
    private @Nullable TextFieldWidget searchBox;
    private @Nullable ButtonWidget friendsButton;
    private @Nullable ButtonWidget pendingButton;
    private @Nullable ButtonWidget addButton;
    private @Nullable Runnable updateListener;
    private @Nullable Runnable presenceListener;
    private @Nullable Text statusMessage;
    private int statusColor = PLAYERNAME_COLOR;
    private final Map<UUID, Identifier> resolvedSkins = new ConcurrentHashMap<>();
    private final Set<UUID> requestedSkins = ConcurrentHashMap.newKeySet();

    public FriendsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    private static Text tr(String key, Object... args) {
        return Text.translatable(key, args);
    }

    private int windowHeight() {
        return Math.max(96, panelBottom() - 64);
    }

    private int listTop() {
        return 96;
    }

    private int doneY() {
        return this.height - 30;
    }

    private int panelBottom() {
        return this.doneY() - 8;
    }

    private int listBottom() {
        return this.panelBottom() - 8;
    }

    private int marginX() {
        return (this.width - 238) / 2;
    }

    @Override
    protected void init() {
        // Player list — 1.19.2 AlwaysSelectedEntryListWidget: (client, width, height, top, bottom, itemHeight)
        int listH = this.listBottom() - this.listTop();
        this.playerList = new FriendsPlayerList(this.client, this.width, listH, this.listTop(), this.listBottom(), ITEM_HEIGHT);

        // Tabs — 1.19.2 ButtonWidget constructor: (x, y, w, h, message, onPress)
        int tabLeft = this.marginX() + 3;
        int tabW = BG_WIDTH / 2;
        this.friendsButton = this.addDrawableChild(
                new ButtonWidget(tabLeft, 45, tabW, 20, TAB_FRIENDS, b -> showPage(Page.FRIENDS)));
        this.pendingButton = this.addDrawableChild(
                new ButtonWidget(tabLeft + tabW, 45, tabW, 20, TAB_PENDING, b -> showPage(Page.PENDING)));

        // Search box
        String oldSearch = this.searchBox != null ? this.searchBox.getText() : "";
        this.searchBox = this.addDrawableChild(
            new TextFieldWidget(this.textRenderer, this.marginX() + 28, 72, 152, 20, SEARCH_HINT));
        this.searchBox.setMaxLength(36);
        this.searchBox.setVisible(true);
        this.searchBox.setEditableColor(-1);
        this.searchBox.setText(oldSearch);
        this.searchBox.setChangedListener(value -> refreshLists());

        // Add button — plain text button (no SpriteIconButton in 1.19.2)
        this.addButton = this.addDrawableChild(
            new ButtonWidget(this.marginX() + 184, 72, 40, 20, BUTTON_ADD, b -> submitFriendRequestFromSearch()));

        // Player list widget
        this.addSelectableChild(this.playerList);

        // Done button
        this.addDrawableChild(
                new ButtonWidget(this.width / 2 - 100, this.doneY(), 200, 20, Text.translatable("gui.done"), b -> this.close()));

        // Listener
        var client = MsfFriendsBoot.get();
        if (client != null && client.social() != null && updateListener == null) {
            updateListener = this::refreshLists;
            client.social().addFriendListUpdateListener(updateListener);
        }
        if (client != null && client.social() != null && presenceListener == null) {
            presenceListener = this::refreshLists;
            client.social().getPresenceHandler().addPresenceListener(presenceListener);
        }

        showPage(this.page);
    }

    @Override
    public void removed() {
        super.removed();
        if (updateListener != null) {
            var client = MsfFriendsBoot.get();
            if (client != null && client.social() != null) {
                client.social().removeFriendListUpdateListener(updateListener);
            }
            updateListener = null;
        }
        if (presenceListener != null) {
            var client = MsfFriendsBoot.get();
            if (client != null && client.social() != null) {
                client.social().getPresenceHandler().removePresenceListener(presenceListener);
            }
            presenceListener = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()
                && (keyCode == 257 || keyCode == 335)) { // Enter or Numpad Enter
            submitFriendRequestFromSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void showPage(Page page) {
        this.page = page;
        if (this.friendsButton != null) this.friendsButton.active = true;
        if (this.pendingButton != null) this.pendingButton.active = true;
        if (this.searchBox != null) {
            this.searchBox.setVisible(true);
            this.searchBox.setEditable(true);
        }
        if (this.addButton != null) {
            this.addButton.visible = true;
            this.addButton.active = true;
        }
        refreshLists();
    }

    private void setStatus(Text message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void submitFriendRequestFromSearch() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.searchBox == null || this.client == null) {
            setStatus(tr("message.msf_friends.service_not_ready"), 0xFFFF8080);
            return;
        }

        String value = this.searchBox.getText().trim();
        if (value.isEmpty()) {
            setStatus(tr("message.msf_friends.enter_player_or_uuid"), 0xFFFFAA00);
            return;
        }

        CompletableFuture<FriendsService.ResultCode> action;
        try {
            action = looksLikeUuid(value)
                    ? client.social().sendFriendRequest(UUID.fromString(value))
                    : client.social().sendFriendRequest(value);
        } catch (IllegalArgumentException ex) {
            setStatus(tr("message.msf_friends.invalid_uuid"), 0xFFFF8080);
            return;
        }

        setStatus(tr("message.msf_friends.sending_friend_request"), 0xFFE0E0E0);
        action.whenComplete((result, error) -> this.client.execute(() -> {
            if (error != null) {
                setStatus(tr("message.msf_friends.friend_request_send_failed", throwableMessage(error)), 0xFFFF8080);
            } else {
                applyResultMessage(tr("message.msf_friends.friend_request_sent"), result);
                if (result == FriendsService.ResultCode.SUCCESS) {
                    this.searchBox.setText("");
                }
            }
            refreshLists();
        }));
    }

    private void applyResultMessage(Text successMessage, FriendsService.ResultCode result) {
        switch (result) {
            case SUCCESS -> setStatus(successMessage, 0xFF55FF55);
            case TOO_MANY_REQUESTS -> setStatus(tr("message.msf_friends.result.too_many_requests"), 0xFFFFAA00);
            case UNKNOWN_PROFILE -> setStatus(tr("message.msf_friends.result.unknown_profile"), 0xFFFF8080);
            case FORBIDDEN -> setStatus(tr("message.msf_friends.result.forbidden"), 0xFFFF8080);
            case SERVICE_NOT_AVAILABLE, TEMPORARY_UNAVAILABLE -> setStatus(tr("message.msf_friends.result.service_not_available"), 0xFFFF8080);
            case CONNECTION_ISSUE -> setStatus(tr("message.msf_friends.result.connection_issue"), 0xFFFF8080);
            case UPGRADE_NEEDED -> setStatus(tr("message.msf_friends.result.upgrade_needed"), 0xFFFF8080);
            case GENERIC_ERROR, ERROR -> setStatus(tr("message.msf_friends.result.generic_error"), 0xFFFF8080);
        }
    }

    private boolean looksLikeUuid(String value) {
        return value.length() == 36 && value.indexOf('-') >= 0;
    }

    private static String throwableMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private boolean hasFilter() {
        return this.searchBox != null && !this.searchBox.getText().trim().isEmpty();
    }

    private boolean matchesFilter(String... values) {
        if (!hasFilter()) return true;
        String filter = this.searchBox != null ? this.searchBox.getText().trim().toLowerCase(Locale.ROOT) : "";
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(filter)) {
                return true;
            }
        }
        return false;
    }

    void refreshLists() {
        if (playerList == null) return;
        List<BaseEntry> entries = new ArrayList<>();

        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) {
            playerList.setEntries(entries);
            return;
        }

        PlayerSocialManager social = client.social();
        PresenceHandler presence = social.getPresenceHandler();
        boolean hosting = client.bridge() != null && client.bridge().isHostingP2P();

        if (page == Page.FRIENDS) {
            List<PresenceStatusDto> pList = presence.getLatestPresence().presence();
            List<PlayerSocialManager.PlayerData> friends = new ArrayList<>(social.getFriends());
            friends.sort((a, b) -> {
                boolean aOnline = pList.stream().anyMatch(p -> p.profileId().equals(a.id()));
                boolean bOnline = pList.stream().anyMatch(p -> p.profileId().equals(b.id()));
                if (aOnline != bOnline) return aOnline ? -1 : 1;
                return String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
            });
            boolean canJoin = this.client != null && this.client.world == null;
            Map<UUID, String> joinRequests = (client.p2p() != null)
                    ? client.p2p().friendJoinHandler().incomingJoinRequestsView()
                    : Map.of();
            for (var f : friends) {
                if (!matchesFilter(f.name(), f.id().toString())) continue;
                Supplier<Identifier> skinGetter = getSkinGetter(f.id(), f.name());
                entries.add(new FriendEntry(this, f, skinGetter, presence, hosting, canJoin, joinRequests));
            }
        } else {
            for (var r : social.getIncomingRequests()) {
                if (!matchesFilter(r.name(), r.id().toString())) continue;
                Supplier<Identifier> skinGetter = getSkinGetter(r.id(), r.name());
                entries.add(new IncomingEntry(this, r, skinGetter));
            }
            for (var r : social.getOutgoingRequests()) {
                if (!matchesFilter(r.name(), r.id().toString())) continue;
                Supplier<Identifier> skinGetter = getSkinGetter(r.id(), r.name());
                entries.add(new OutgoingEntry(this, r, skinGetter));
            }
        }

        playerList.setEntries(entries);
    }

    private Supplier<Identifier> getSkinGetter(UUID profileId, String name) {
        return () -> {
            Identifier skin = this.resolvedSkins.get(profileId);
            if (skin != null) return skin;

            if (this.client != null && this.requestedSkins.add(profileId)) {
                PlayerSkinResolver.fetchSkin(this.client, profileId, name)
                        .thenAccept(resolved -> this.resolvedSkins.put(profileId, resolved));
            }

            return DefaultSkinHelper.getTexture(profileId);
        };
    }

    /** Get a skin getter by pmid, mapping back to profile ID via PresenceHandler. */
    private Supplier<Identifier> getSkinGetterByPmid(PresenceHandler presence, UUID pmid) {
        UUID profileId = presence.getProfileIdFromPmid(pmid);
        if (profileId != null) return getSkinGetter(profileId, "");
        return getSkinGetter(pmid, "");
    }

    /** Find a friend's display name by their pmid, mapping back to profile ID. */
    private String findNameByPmid(PlayerSocialManager social, PresenceHandler presence, UUID pmid) {
        UUID profileId = presence.getProfileIdFromPmid(pmid);
        if (profileId != null) {
            return social.getFriends().stream()
                    .filter(f -> f.id().equals(profileId))
                    .map(PlayerSocialManager.PlayerData::name)
                    .findFirst().orElse("???");
        }
        return "???";
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float a) {
        int mx = this.marginX() + 3;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        drawTexture(matrixStack, mx, 64, 0, 0, BG_WIDTH, this.windowHeight(), 256, 256);

        this.renderBackground(matrixStack);
        // Dim the unselected tab
        ButtonWidget unselTab = (page == Page.FRIENDS) ? pendingButton : friendsButton;
        if (unselTab != null) {
            fill(matrixStack, unselTab.x, unselTab.y,
                    unselTab.x + unselTab.getWidth(),
                    unselTab.y + unselTab.getHeight(), 0x99000000);
        }
        // Title
        drawCenteredText(matrixStack, this.textRenderer, TITLE, this.width / 2, 8, -1);
        if (this.statusMessage != null) {
                drawCenteredText(matrixStack, this.textRenderer, this.statusMessage, this.width / 2, 24, this.statusColor);
        }
        // List or empty message
        if (playerList != null && !playerList.children().isEmpty()) {
            playerList.render(matrixStack, mouseX, mouseY, a);
        } else {
            Text empty = hasFilter() ? EMPTY_FILTER : (page == Page.FRIENDS ? EMPTY_FRIENDS : EMPTY_PENDING);
            drawCenteredText(matrixStack, this.textRenderer, empty, this.width / 2, (this.listTop() + this.listBottom()) / 2, 0xAAAAAA);
        }
        // Render widgets on top
        super.render(matrixStack, mouseX, mouseY, a);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    enum Page { FRIENDS, PENDING }

    // ============ Player List ============

    static class FriendsPlayerList extends AlwaysSelectedEntryListWidget<BaseEntry> {
        public FriendsPlayerList(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        /** Public wrapper for the protected {@code replaceEntries} method. */
        public void setEntries(List<BaseEntry> entries) {
            replaceEntries(entries);
        }

        @Override
        public int getRowWidth() {
            return 200;
        }

        @Override
        protected int getScrollbarPositionX() {
            return this.left + this.width / 2 + 100;
        }
    }

    // ============ Entry base ============

    static abstract class BaseEntry extends AlwaysSelectedEntryListWidget.Entry<BaseEntry> {
        protected final MinecraftClient minecraft = MinecraftClient.getInstance();
        protected final FriendsScreen screen;
        protected final String playerName;
        protected final Supplier<Identifier> skinGetter;
        protected final List<net.minecraft.client.gui.widget.ClickableWidget> children = new ArrayList<>();

        BaseEntry(FriendsScreen screen, String name, Supplier<Identifier> skinGetter) {
            this.screen = screen;
            this.playerName = name;
            this.skinGetter = skinGetter;
        }

        @Override
        public void render(MatrixStack matrixStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
            // Hover highlight
            if (hovering) {
                fill(matrixStack, left, top, left + width, top + height, 0x22FFFFFF);
            }

            // Face
            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            Identifier skin = skinGetter.get();
            renderFace(matrixStack, skin, skinX, skinY);

            // Name
            int textX = skinX + SKIN_SIZE + 4;
            int nameY = top + (height - 9) / 2;
            minecraft.textRenderer.draw(matrixStack, playerName, textX, nameY, PLAYERNAME_COLOR);
        }

        protected void renderFace(MatrixStack matrixStack, Identifier skinLoc, int x, int y) {
            RenderSystem.setShaderTexture(0, skinLoc);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableBlend();
            int s = SKIN_SIZE;
            // drawTexture(MatrixStack, x, y, width, height, u, v, regionW, regionH, texW, texH)
            drawTexture(matrixStack, x, y, s, s, 8f, 8f, 8, 8, 64, 64);
            drawTexture(matrixStack, x, y, s, s, 40f, 8f, 8, 8, 64, 64);
            RenderSystem.disableBlend();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (var child : children) {
                if (child.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return false;
        }

        @Override
        public Text getNarration() {
            return Text.literal(playerName);
        }
    }

    // ============ Friend entry ============

    static class FriendEntry extends BaseEntry {
        private final @Nullable Text status;
        private final int statusColor;
        private final ButtonWidget removeBtn;
        private final @Nullable ButtonWidget actionBtn;
        private final @Nullable ButtonWidget acceptBtn;
        private final @Nullable ButtonWidget rejectBtn;
        private final @Nullable ButtonWidget pendingLoadingBtn; // loading overlay while join request is pending

        FriendEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data,
                   Supplier<Identifier> skinGetter, PresenceHandler presenceHandler,
                   boolean hosting, boolean canJoin, Map<UUID, String> incomingJoinRequests) {
            super(screen, data.name(), skinGetter);

            PresenceStatusDto pres = presenceHandler.getLatestPresence().presence().stream()
                    .filter(p -> p.profileId().equals(data.id())).findFirst().orElse(null);

            this.status = statusText(pres);
            this.statusColor = statusColor(pres);

            // Remove button
            removeBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.remove_friend"), b -> {
                if (this.minecraft == null) return;
                final String friendName = data.name();
                this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> {
                        this.minecraft.setScreen(screen);
                        if (confirmed) {
                            var c = MsfFriendsBoot.get();
                            if (c != null && c.social() != null) {
                                c.social().removeFriend(data.id()).whenComplete((result, err) -> {
                                    if (result == FriendsService.ResultCode.SUCCESS) {
                                        this.minecraft.execute(() -> FriendToast.show(
                                            tr("screen.msf_friends.friends.toast.friend_removed.title"),
                                            tr("screen.msf_friends.friends.toast.friend_removed.description", friendName),
                                            data.id()
                                        ));
                                    }
                                });
                            }
                        }
                    },
                    tr("screen.msf_friends.friends.confirm_remove.title"),
                    tr("screen.msf_friends.friends.confirm_remove.message", data.name())
                ));
            });
            children.add(removeBtn);

            UUID friendPmid = pres != null ? pres.pmid() : null;
            boolean hasPendingJoinReq = hosting && friendPmid != null
                    && incomingJoinRequests.containsKey(friendPmid);

            if (pres == null) {
                // 离线：只保留删除按钮
                actionBtn = null;
                acceptBtn = null;
                rejectBtn = null;
                pendingLoadingBtn = null;
            } else if (hasPendingJoinReq) {
                // 该好友发来了加入请求：显示同意/拒绝
                actionBtn = null;
                pendingLoadingBtn = null;
                UUID reqPmid = friendPmid;
                acceptBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.accept_join"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        c.p2p().acceptIncomingJoinRequest(reqPmid);
                        screen.refreshLists();
                    }
                });
                children.add(acceptBtn);
                rejectBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.reject_join"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        c.p2p().rejectIncomingJoinRequest(reqPmid);
                        screen.refreshLists();
                    }
                });
                children.add(rejectBtn);
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin
                    && !presenceHandler.hasDismissedInvite(pres)
                    && pres.joinInfo() != null && pres.joinInfo().invited()) {
                // 房主已邀请我：接受邀请 = 走与「请求加入」相同的连接流程
                actionBtn = null;
                UUID pmid = friendPmid;
                ButtonWidget loadBtn2 = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.connecting"), b -> {});
                loadBtn2.active = false;
                loadBtn2.visible = false;
                children.add(loadBtn2);
                pendingLoadingBtn = loadBtn2;
                acceptBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.accept_invite"), b -> {
                    b.visible = false;
                    loadBtn2.active = true;
                    loadBtn2.visible = true;
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        var p2p = c.p2p();
                        Runnable[] lr = {null};
                        lr[0] = () -> {
                            var st = p2p.outgoingJoinState(pmid);
                            if (st == FriendJoinHandler.OutgoingJoinState.CONNECTING
                                    || st == FriendJoinHandler.OutgoingJoinState.CONNECTED) {
                                p2p.removeJoinStateListener(lr[0]);
                                P2PConnectScreen.show(screen.parent, pmid.toString());
                            } else if (st == FriendJoinHandler.OutgoingJoinState.NONE) {
                                p2p.removeJoinStateListener(lr[0]);
                                screen.refreshLists();
                            }
                        };
                        p2p.addJoinStateListener(lr[0]);
                        p2p.joinPlayer(pmid.toString())
                                .exceptionally(err -> {
                                    p2p.removeJoinStateListener(lr[0]);
                                    screen.refreshLists();
                                    return null;
                                });
                    }
                });
                children.add(acceptBtn);
                rejectBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.reject_invite"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null) {
                        if (c.p2p() != null) c.p2p().declineInvite(pmid);
                        if (c.social() != null) c.social().getPresenceHandler().dismissInviteForPmid(pmid);
                        screen.refreshLists();
                    }
                });
                children.add(rejectBtn);
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin) {
                // 好友正在托管，未受邀：发送申请，等对方同意后再显示连接遮罩
                acceptBtn = null;
                rejectBtn = null;
                UUID pmid = friendPmid;
                ButtonWidget loadBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.requesting"), b -> {});
                loadBtn.active = false;
                loadBtn.visible = false;
                children.add(loadBtn);
                pendingLoadingBtn = loadBtn;
                actionBtn = new ButtonWidget(0, 0, 60, 20, Text.translatable("screen.msf_friends.friends.button.request_join"), b -> {
                    b.visible = false;
                    loadBtn.active = true;
                    loadBtn.visible = true;
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        var p2p = c.p2p();
                        Runnable[] lr = {null};
                        lr[0] = () -> {
                            var st = p2p.outgoingJoinState(pmid);
                            if (st == FriendJoinHandler.OutgoingJoinState.CONNECTING
                                    || st == FriendJoinHandler.OutgoingJoinState.CONNECTED) {
                                p2p.removeJoinStateListener(lr[0]);
                                P2PConnectScreen.show(screen.parent, pmid.toString());
                            } else if (st == FriendJoinHandler.OutgoingJoinState.NONE) {
                                p2p.removeJoinStateListener(lr[0]);
                                screen.refreshLists();
                            }
                        };
                        p2p.addJoinStateListener(lr[0]);
                        p2p.joinPlayer(pmid.toString())
                                .exceptionally(err -> {
                                    p2p.removeJoinStateListener(lr[0]);
                                    screen.refreshLists();
                                    return null;
                                });
                    }
                });
                children.add(actionBtn);
            } else if (hosting && pres != null && "ONLINE".equals(pres.status().name())
                    && !presenceHandler.getInvitedPlayersBatch().contains(data.id())) {
                // 我是房主且好友在线：显示邀请按钮
                acceptBtn = null;
                rejectBtn = null;
                actionBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.invite"), b -> {
                    b.active = false;
                    presenceHandler.invitePlayer(data.id());
                });
                children.add(actionBtn);
                pendingLoadingBtn = null;
            } else {
                actionBtn = null;
                acceptBtn = null;
                rejectBtn = null;
                pendingLoadingBtn = null;
            }
        }

        static Text statusText(@Nullable PresenceStatusDto p) {
            if (p == null) return tr("screen.msf_friends.friends.presence.offline");
            return switch (p.status().name()) {
                case "ONLINE" -> tr("screen.msf_friends.friends.presence.online");
                case "PLAYING_OFFLINE" -> tr("screen.msf_friends.friends.presence.playing_offline");
                case "PLAYING_HOSTED_SERVER" -> tr("screen.msf_friends.friends.presence.hosting");
                case "PLAYING_REALMS" -> tr("screen.msf_friends.friends.presence.realms");
                case "PLAYING_SERVER" -> tr("screen.msf_friends.friends.presence.server");
                default -> tr("screen.msf_friends.friends.presence.offline");
            };
        }

        static int statusColor(@Nullable PresenceStatusDto p) {
            if (p == null) return 0xFFAAAAAA;
            return switch (p.status().name()) {
                case "ONLINE" -> 0xFF55FF55;
                case "PLAYING_OFFLINE" -> 0xFFAAAAAA;
                case "PLAYING_HOSTED_SERVER" -> 0xFFFFAA00;
                case "PLAYING_REALMS" -> 0xFFFF55FF;
                case "PLAYING_SERVER" -> 0xFF55AAFF;
                default -> 0xFFAAAAAA;
            };
        }

        @Override
        public void render(MatrixStack matrixStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
            // Hover highlight
            if (hovering) fill(matrixStack, left, top, left + width, top + height, 0x22FFFFFF);

            // Face
            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            renderFace(matrixStack, skinGetter.get(), skinX, skinY);

            // Name (top half)
            int textX = skinX + SKIN_SIZE + 4;
            int nameY = top + height / 3 - 9 / 2;
            minecraft.textRenderer.draw(matrixStack, playerName, textX, nameY, PLAYERNAME_COLOR);

            // Status (bottom half)
            if (status != null) {
                minecraft.textRenderer.draw(matrixStack, status, textX, nameY + 12, statusColor);
            }

            // Buttons from right
            int btnX = left + width - 4;
            btnX -= 42;
            removeBtn.x = btnX;
            removeBtn.y = top + (height - 20) / 2;
            removeBtn.render(matrixStack, mouseX, mouseY, delta);

            if (acceptBtn != null && rejectBtn != null) {
                btnX -= 42;
                rejectBtn.x = btnX;
                rejectBtn.y = top + (height - 20) / 2;
                rejectBtn.render(matrixStack, mouseX, mouseY, delta);
                btnX -= 42;
                acceptBtn.x = btnX;
                acceptBtn.y = top + (height - 20) / 2;
                acceptBtn.render(matrixStack, mouseX, mouseY, delta);
            } else if (actionBtn != null) {
                btnX -= 62;
                actionBtn.x = btnX;
                actionBtn.y = top + (height - 20) / 2;
                actionBtn.render(matrixStack, mouseX, mouseY, delta);
            }
            // Loading overlay (visible after request/invite sent, while waiting for connection)
            if (pendingLoadingBtn != null && pendingLoadingBtn.visible) {
                pendingLoadingBtn.x = left + width / 2 - 30;
                pendingLoadingBtn.y = top + (height - 20) / 2;
                pendingLoadingBtn.render(matrixStack, mouseX, mouseY, delta);
            }
        }
    }

    // ============ Incoming request entry ============

    static class IncomingEntry extends BaseEntry {
        private final PlayerSocialManager.PlayerData data;
        private final ButtonWidget acceptBtn;
        private final ButtonWidget rejectBtn;

        IncomingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<Identifier> skinGetter) {
            super(screen, data.name(), skinGetter);
            this.data = data;
            acceptBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.accept_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) {
                    c.social().acceptIncomingFriendRequest(data.id()).whenComplete((result, err) -> {
                        if (this.minecraft != null) this.minecraft.execute(screen::refreshLists);
                    });
                }
            });
            rejectBtn = new ButtonWidget(0, 0, 40, 20, Text.translatable("screen.msf_friends.friends.button.decline_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) {
                    c.social().declineIncomingFriendRequest(data.id()).whenComplete((result, err) -> {
                        if (this.minecraft != null) this.minecraft.execute(screen::refreshLists);
                    });
                }
            });
            children.add(acceptBtn);
            children.add(rejectBtn);
        }

        @Override
        public void render(MatrixStack matrixStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
            if (hovering) fill(matrixStack, left, top, left + width, top + height, 0x22FFFFFF);

            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            renderFace(matrixStack, skinGetter.get(), skinX, skinY);

            int textX = skinX + SKIN_SIZE + 4;
            minecraft.textRenderer.draw(matrixStack, playerName, textX, top + (height - 9) / 2, PLAYERNAME_COLOR);

            int btnX = left + width - 4;
            btnX -= 42;
            rejectBtn.x = btnX;
            rejectBtn.y = top + (height - 20) / 2;
            rejectBtn.render(matrixStack, mouseX, mouseY, delta);
            btnX -= 42;
            acceptBtn.x = btnX;
            acceptBtn.y = top + (height - 20) / 2;
            acceptBtn.render(matrixStack, mouseX, mouseY, delta);
        }
    }

    // ============ Outgoing request entry ============

    static class OutgoingEntry extends BaseEntry {
        private final ButtonWidget cancelBtn;

        OutgoingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<Identifier> skinGetter) {
            super(screen, data.name(), skinGetter);
            cancelBtn = new ButtonWidget(0, 0, 50, 20, Text.translatable("screen.msf_friends.friends.button.revoke_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) {
                    c.social().revokeOutgoingFriendRequest(data.id()).whenComplete((result, err) -> {
                        if (this.minecraft != null) this.minecraft.execute(screen::refreshLists);
                    });
                }
            });
            children.add(cancelBtn);
        }

        @Override
        public void render(MatrixStack matrixStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
            if (hovering) fill(matrixStack, left, top, left + width, top + height, 0x22FFFFFF);

            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            renderFace(matrixStack, skinGetter.get(), skinX, skinY);

            int textX = skinX + SKIN_SIZE + 4;
            minecraft.textRenderer.draw(matrixStack, playerName, textX, top + (height - 9) / 2, PLAYERNAME_COLOR);

            cancelBtn.x = left + width - 54;
            cancelBtn.y = top + (height - 20) / 2;
            cancelBtn.render(matrixStack, mouseX, mouseY, delta);
        }
    }
}
