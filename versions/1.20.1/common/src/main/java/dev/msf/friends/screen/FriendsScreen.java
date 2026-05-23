package dev.msf.friends.screen;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.FriendJoinHandler;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.PresenceHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
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

public class FriendsScreen extends Screen {
    private static final Text TITLE = tr("screen.msf_friends.friends.title");
    private static final Identifier BACKGROUND_TEXTURE = new Identifier("minecraft", "textures/gui/social_interactions.png");
    private static final Identifier SEARCH_ICON = new Identifier("msf_friends", "textures/gui/sprites/icon/friends.png");
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

    private int listTop() { return 96; }
    private int doneY() { return this.height - 30; }
    private int panelBottom() { return this.doneY() - 8; }
    private int listBottom() { return this.panelBottom() - 8; }
    private int marginX() { return (this.width - 238) / 2; }

    @Override
    protected void init() {
        // Player list — 1.20.1 AlwaysSelectedEntryListWidget: (client, width, height, top, bottom, itemHeight)
        int listH = this.listBottom() - this.listTop();
        this.playerList = new FriendsPlayerList(this.client, this.width, listH, this.listTop(), this.listBottom(), ITEM_HEIGHT);

        // Tabs
        int tabLeft = this.marginX() + 3;
        int tabW = BG_WIDTH / 2;
        this.friendsButton = this.addDrawableChild(
                ButtonWidget.builder(TAB_FRIENDS, b -> showPage(Page.FRIENDS))
                        .dimensions(tabLeft, 45, tabW, 20).build());
        this.pendingButton = this.addDrawableChild(
                ButtonWidget.builder(TAB_PENDING, b -> showPage(Page.PENDING))
                        .dimensions(tabLeft + tabW, 45, tabW, 20).build());

        // Search box
        String oldSearch = this.searchBox != null ? this.searchBox.getText() : "";
        this.searchBox = this.addDrawableChild(
            new TextFieldWidget(this.textRenderer, this.marginX() + 28, 72, 152, 20, SEARCH_HINT));
        this.searchBox.setMaxLength(36);
        this.searchBox.setVisible(true);
        this.searchBox.setEditableColor(-1);
        this.searchBox.setText(oldSearch);
        this.searchBox.setSuggestion(SEARCH_HINT.getString());
        this.searchBox.setChangedListener(value -> refreshLists());

        this.addButton = this.addDrawableChild(
            IconButtonWidget.builder(BUTTON_ADD, b -> submitFriendRequestFromSearch(),
                    new Identifier("msf_friends", "textures/gui/sprites/icon/join.png"), 12, 12)
                .dimensions(this.marginX() + 184, 72, 20, 20).build());

        this.addSelectableChild(this.playerList);

        // Done button — use Text.translatable instead of ScreenTexts.GUI_DONE
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.done"), b -> this.close())
                .dimensions(this.width / 2 - 100, this.doneY(), 200, 20).build());

        // Listeners
        var msfClient = MsfFriendsBoot.get();
        if (msfClient != null && msfClient.social() != null && updateListener == null) {
            updateListener = this::refreshLists;
            msfClient.social().addFriendListUpdateListener(updateListener);
        }
        if (msfClient != null && msfClient.social() != null && presenceListener == null) {
            presenceListener = this::refreshLists;
            msfClient.social().getPresenceHandler().addPresenceListener(presenceListener);
        }

        showPage(this.page);
    }

    @Override
    public void removed() {
        super.removed();
        if (updateListener != null) {
            var msfClient = MsfFriendsBoot.get();
            if (msfClient != null && msfClient.social() != null) {
                msfClient.social().removeFriendListUpdateListener(updateListener);
            }
            updateListener = null;
        }
        if (presenceListener != null) {
            var msfClient = MsfFriendsBoot.get();
            if (msfClient != null && msfClient.social() != null) {
                msfClient.social().getPresenceHandler().removePresenceListener(presenceListener);
            }
            presenceListener = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()
                && keyCode == 257 /* GLFW_KEY_ENTER */) {
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
        setButtonVisible(this.addButton, true);
        refreshLists();
    }

    private static void setButtonVisible(@Nullable ButtonWidget button, boolean visible) {
        if (button == null) return;
        button.visible = visible;
        button.active = visible;
    }

    private void setStatus(Text message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void submitFriendRequestFromSearch() {
        var msfClient = MsfFriendsBoot.get();
        if (msfClient == null || msfClient.social() == null || this.searchBox == null || this.client == null) {
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
                    ? msfClient.social().sendFriendRequest(UUID.fromString(value))
                    : msfClient.social().sendFriendRequest(value);
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
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private boolean hasFilter() {
        return this.searchBox != null && !this.searchBox.getText().trim().isEmpty();
    }

    private boolean matchesFilter(String... values) {
        if (!hasFilter()) return true;
        String filter = this.searchBox != null ? this.searchBox.getText().trim().toLowerCase(Locale.ROOT) : "";
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(filter)) return true;
        }
        return false;
    }

    void refreshLists() {
        if (playerList == null) return;
        List<BaseEntry> entries = new ArrayList<>();
        var msfClient = MsfFriendsBoot.get();
        if (msfClient == null || msfClient.social() == null) {
            playerList.setEntries(entries);
            return;
        }

        PlayerSocialManager social = msfClient.social();
        PresenceHandler presence = social.getPresenceHandler();
        boolean hosting = msfClient.bridge() != null && msfClient.bridge().isHostingP2P();

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
            Map<UUID, String> joinRequests = (msfClient.p2p() != null)
                    ? msfClient.p2p().friendJoinHandler().incomingJoinRequestsView()
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
            MinecraftClient minecraft = this.client;
            if (minecraft != null && this.requestedSkins.add(profileId)) {
                PlayerSkinResolver.fetchSkin(minecraft, profileId, name)
                        .thenAccept(resolved -> this.resolvedSkins.put(profileId, resolved));
            }
            return DefaultSkinHelper.getTexture(profileId);
        };
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // Background panel
        int mx = this.marginX() + 3;
        int winH = Math.max(96, panelBottom() - 64);
        context.drawTexture(BACKGROUND_TEXTURE, mx, 64, 0, 0, BG_WIDTH, winH);
        // Search icon
        context.drawTexture(SEARCH_ICON, mx + 10, 76, 0, 0, 12, 12, 12, 12);

        // Dim the unselected tab
        ButtonWidget unselTab = (page == Page.FRIENDS) ? pendingButton : friendsButton;
        if (unselTab != null) {
            context.fill(unselTab.getX(), unselTab.getY(),
                    unselTab.getX() + unselTab.getWidth(),
                    unselTab.getY() + unselTab.getHeight(), 0x99000000);
        }

        // Title — 1.20.1 Yarn uses drawCenteredTextWithShadow
        context.drawTextWithShadow(this.textRenderer, TITLE, this.width / 2 - this.textRenderer.getWidth(TITLE) / 2, 8, -1);
        if (this.statusMessage != null) {
            context.drawTextWithShadow(this.textRenderer, this.statusMessage, this.width / 2 - this.textRenderer.getWidth(this.statusMessage) / 2, 24, this.statusColor);
        }

        // List or empty message
        if (playerList != null && !playerList.children().isEmpty()) {
            playerList.render(context, mouseX, mouseY, delta);
        } else {
            Text empty = hasFilter() ? EMPTY_FILTER : (page == Page.FRIENDS ? EMPTY_FRIENDS : EMPTY_PENDING);
            context.drawTextWithShadow(this.textRenderer, empty, this.width / 2 - this.textRenderer.getWidth(empty) / 2, (this.listTop() + this.listBottom()) / 2, -1);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    enum Page { FRIENDS, PENDING }

    // ============ Player List ============

    static class FriendsPlayerList extends AlwaysSelectedEntryListWidget<BaseEntry> {
        public FriendsPlayerList(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        public void setEntries(List<BaseEntry> entries) {
            replaceEntries(entries);
        }

        @Override
        public int getRowWidth() { return 200; }

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
        protected final List<ClickableWidget> children = new ArrayList<>();

        BaseEntry(FriendsScreen screen, String name, Supplier<Identifier> skinGetter) {
            this.screen = screen;
            this.playerName = name;
            this.skinGetter = skinGetter;
        }

        public List<ClickableWidget> children() { return children; }

        /** Draw skin + name on one line. */
        protected void renderBase(DrawContext context, int y, int x, int entryWidth, int entryHeight, boolean hovered) {
            if (hovered) context.fill(x, y, x + entryWidth, y + entryHeight, 0x22FFFFFF);
            int skinX = x + 4;
            int skinY = y + (entryHeight - SKIN_SIZE) / 2;
            Identifier skinTexture = skinGetter.get();
            PlayerSkinDrawer.draw(context, skinTexture, skinX, skinY, SKIN_SIZE);
            int textX = skinX + SKIN_SIZE + 4;
            context.drawTextWithShadow(minecraft.textRenderer, playerName, textX, y + (entryHeight - 9) / 2, PLAYERNAME_COLOR);
        }

        @Override
        public Text getNarration() { return Text.literal(playerName); }
    }

    // ============ Friend entry ============

    static class FriendEntry extends BaseEntry {
        private final @Nullable Text status;
        private final int statusColor;
        private final ButtonWidget removeBtn;
        private final @Nullable ButtonWidget actionBtn;
        private final @Nullable ButtonWidget acceptBtn;
        private final @Nullable ButtonWidget rejectBtn;
        private final @Nullable IconButtonWidget pendingLoadingBtn;

        FriendEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data,
                   Supplier<Identifier> skinGetter, PresenceHandler presenceHandler,
                   boolean hosting, boolean canJoin, Map<UUID, String> incomingJoinRequests) {
            super(screen, data.name(), skinGetter);

            PresenceStatusDto pres = presenceHandler.getLatestPresence().presence().stream()
                    .filter(p -> p.profileId().equals(data.id())).findFirst().orElse(null);
            this.status = statusText(pres);
            this.statusColor = statusColor(pres);

            removeBtn = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.remove_friend"), b -> {
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
            }, new Identifier("msf_friends", "textures/gui/sprites/icon/remove.png"), 12, 12)
                .dimensions(0, 0, 20, 20).build();
            children.add(removeBtn);

            UUID friendPmid = pres != null ? pres.pmid() : null;
            boolean hasPendingJoinReq = hosting && friendPmid != null
                    && incomingJoinRequests.containsKey(friendPmid);

            if (pres == null) {
                // Offline
                actionBtn = null; acceptBtn = null; rejectBtn = null; pendingLoadingBtn = null;
            } else if (hasPendingJoinReq) {
                // Join request: accept/reject
                actionBtn = null;
                UUID reqPmid = friendPmid;
                ButtonWidget ab = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.accept_join"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) { c.p2p().acceptIncomingJoinRequest(reqPmid); screen.refreshLists(); }
                }, new Identifier("msf_friends", "textures/gui/sprites/icon/join.png"), 12, 12)
                    .dimensions(0, 0, 20, 20).build();
                children.add(ab);
                acceptBtn = ab;
                ButtonWidget rb = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.reject_join"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) { c.p2p().rejectIncomingJoinRequest(reqPmid); screen.refreshLists(); }
                }, new Identifier("msf_friends", "textures/gui/sprites/icon/reject.png"), 12, 12)
                    .dimensions(0, 0, 20, 20).build();
                children.add(rb);
                rejectBtn = rb;
                pendingLoadingBtn = null;
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin
                    && !presenceHandler.hasDismissedInvite(pres)
                    && pres.joinInfo() != null && pres.joinInfo().invited()) {
                // Invited: accept_invite / reject_invite with loading overlay
                actionBtn = null;
                UUID pmid = friendPmid;
                IconButtonWidget loadBtn = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.connecting"), b -> {}, true,
                    new Identifier("msf_friends", "textures/gui/sprites/icon/loading.png"), 12, 12)
                    .dimensions(0, 0, 20, 20).build();
                loadBtn.active = false; loadBtn.visible = false;
                children.add(loadBtn);
                pendingLoadingBtn = loadBtn;
                ButtonWidget ab = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.accept_invite"), b -> {
                    b.visible = false;
                    loadBtn.active = true; loadBtn.visible = true;
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        var p2p = c.p2p();
                        Runnable[] lr = {null};
                        lr[0] = () -> {
                            var st = p2p.outgoingJoinState(pmid);
                            if (st == FriendJoinHandler.OutgoingJoinState.CONNECTING || st == FriendJoinHandler.OutgoingJoinState.CONNECTED) {
                                p2p.removeJoinStateListener(lr[0]);
                                P2PConnectScreen.show(screen.parent, pmid.toString());
                            } else if (st == FriendJoinHandler.OutgoingJoinState.NONE) {
                                p2p.removeJoinStateListener(lr[0]);
                                screen.refreshLists();
                            }
                        };
                        p2p.addJoinStateListener(lr[0]);
                        p2p.joinPlayer(pmid.toString()).exceptionally(err -> { p2p.removeJoinStateListener(lr[0]); screen.refreshLists(); return null; });
                    }
                }, new Identifier("msf_friends", "textures/gui/sprites/icon/join.png"), 12, 12)
                    .dimensions(0, 0, 20, 20).build();
                children.add(ab);
                acceptBtn = ab;
                ButtonWidget rb = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.reject_invite"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null) {
                        if (c.p2p() != null) c.p2p().declineInvite(pmid);
                        if (c.social() != null) c.social().getPresenceHandler().dismissInviteForPmid(pmid);
                        screen.refreshLists();
                    }
                }, new Identifier("msf_friends", "textures/gui/sprites/icon/reject.png"), 12, 12)
                    .dimensions(0, 0, 20, 20).build();
                children.add(rb);
                rejectBtn = rb;
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin) {
                // Not invited: request join with loading overlay
                acceptBtn = null; rejectBtn = null;
                UUID pmid = friendPmid;
                IconButtonWidget loadBtn = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.requesting"), b -> {}, true,
                    new Identifier("msf_friends", "textures/gui/sprites/icon/loading.png"), 12, 12)
                    .dimensions(0, 0, 20, 20).build();
                loadBtn.active = false; loadBtn.visible = false;
                children.add(loadBtn);
                pendingLoadingBtn = loadBtn;
                ButtonWidget ab = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.request_join"), b -> {
                    b.visible = false;
                    loadBtn.active = true; loadBtn.visible = true;
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        var p2p = c.p2p();
                        Runnable[] lr = {null};
                        lr[0] = () -> {
                            var st = p2p.outgoingJoinState(pmid);
                            if (st == FriendJoinHandler.OutgoingJoinState.CONNECTING || st == FriendJoinHandler.OutgoingJoinState.CONNECTED) {
                                p2p.removeJoinStateListener(lr[0]);
                                P2PConnectScreen.show(screen.parent, pmid.toString());
                            } else if (st == FriendJoinHandler.OutgoingJoinState.NONE) {
                                p2p.removeJoinStateListener(lr[0]);
                                screen.refreshLists();
                            }
                        };
                        p2p.addJoinStateListener(lr[0]);
                        p2p.joinPlayer(pmid.toString()).exceptionally(err -> { p2p.removeJoinStateListener(lr[0]); screen.refreshLists(); return null; });
                    }
                }, new Identifier("msf_friends", "textures/gui/sprites/icon/join_request.png"), 12, 12)
                    .dimensions(0, 0, 20, 20).build();
                children.add(ab);
                actionBtn = ab;
            } else if (hosting && pres != null && "ONLINE".equals(pres.status().name())
                    && !presenceHandler.getInvitedPlayersBatch().contains(data.id())) {
                // Host + friend online: invite
                acceptBtn = null; rejectBtn = null; pendingLoadingBtn = null;
                ButtonWidget ab = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.invite"), b -> {
                    b.active = false;
                    presenceHandler.invitePlayer(data.id());
                }, new Identifier("msf_friends", "textures/gui/sprites/icon/join.png"), 12, 12)
                    .dimensions(0, 0, 20, 20).build();
                children.add(ab);
                actionBtn = ab;
            } else {
                actionBtn = null; acceptBtn = null; rejectBtn = null; pendingLoadingBtn = null;
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
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (hovered) context.fill(x, y, x + entryWidth, y + entryHeight, 0x22FFFFFF);

            // Face
            int skinX = x + 4;
            int skinY = y + (entryHeight - SKIN_SIZE) / 2;
            Identifier skinTexture = skinGetter.get();
            PlayerSkinDrawer.draw(context, skinTexture, skinX, skinY, SKIN_SIZE);

            // Name + status
            int textX = skinX + SKIN_SIZE + 4;
            int nameY = y + entryHeight / 3 - minecraft.textRenderer.fontHeight / 2;
            context.drawTextWithShadow(minecraft.textRenderer, playerName, textX, nameY, PLAYERNAME_COLOR);
            if (status != null) {
                context.drawTextWithShadow(minecraft.textRenderer, status, textX, nameY + 12, statusColor);
            }

            // Buttons from right
            int btnX = x + entryWidth - 4;
            btnX -= 20;
            removeBtn.setX(btnX); removeBtn.setY(y + (entryHeight - 20) / 2);
            removeBtn.render(context, mouseX, mouseY, tickDelta);

            if (acceptBtn != null && rejectBtn != null) {
                btnX -= 22;
                rejectBtn.setX(btnX); rejectBtn.setY(y + (entryHeight - 20) / 2);
                if (rejectBtn.visible) rejectBtn.render(context, mouseX, mouseY, tickDelta);
                btnX -= 22;
                if (pendingLoadingBtn != null && pendingLoadingBtn.visible) {
                    pendingLoadingBtn.setX(btnX); pendingLoadingBtn.setY(y + (entryHeight - 20) / 2);
                    pendingLoadingBtn.render(context, mouseX, mouseY, tickDelta);
                } else if (acceptBtn.visible) {
                    acceptBtn.setX(btnX); acceptBtn.setY(y + (entryHeight - 20) / 2);
                    acceptBtn.render(context, mouseX, mouseY, tickDelta);
                }
            } else if (actionBtn != null) {
                btnX -= actionBtn.getWidth() + 2;
                int abx = btnX, aby = y + (entryHeight - 20) / 2;
                actionBtn.setX(abx); actionBtn.setY(aby);
                if (actionBtn.visible) actionBtn.render(context, mouseX, mouseY, tickDelta);
                if (pendingLoadingBtn != null) {
                    pendingLoadingBtn.setX(abx); pendingLoadingBtn.setY(aby);
                    if (pendingLoadingBtn.visible) pendingLoadingBtn.render(context, mouseX, mouseY, tickDelta);
                }
            }
        }
    }

    // ============ Incoming friend request ============

    static class IncomingEntry extends BaseEntry {
        private final ButtonWidget acceptBtn, declineBtn;

        IncomingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<Identifier> skinGetter) {
            super(screen, data.name(), skinGetter);
            acceptBtn = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.accept_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) c.social().acceptIncomingFriendRequest(data.id());
            }, new Identifier("msf_friends", "textures/gui/sprites/icon/join.png"), 12, 12)
                .dimensions(0, 0, 20, 20).build();
            children.add(acceptBtn);
            declineBtn = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.decline_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) c.social().declineIncomingFriendRequest(data.id());
            }, new Identifier("msf_friends", "textures/gui/sprites/icon/reject.png"), 12, 12)
                .dimensions(0, 0, 20, 20).build();
            children.add(declineBtn);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            renderBase(context, y, x, entryWidth, entryHeight, hovered);
            int btnX = x + entryWidth - 4;
            btnX -= 20;
            declineBtn.setX(btnX); declineBtn.setY(y + (entryHeight - 20) / 2);
            declineBtn.render(context, mouseX, mouseY, tickDelta);
            btnX -= 22;
            acceptBtn.setX(btnX); acceptBtn.setY(y + (entryHeight - 20) / 2);
            acceptBtn.render(context, mouseX, mouseY, tickDelta);
        }
    }

    // ============ Outgoing friend request ============

    static class OutgoingEntry extends BaseEntry {
        private final ButtonWidget revokeBtn;

        OutgoingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<Identifier> skinGetter) {
            super(screen, data.name(), skinGetter);
            revokeBtn = IconButtonWidget.builder(tr("screen.msf_friends.friends.button.revoke_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) c.social().revokeOutgoingFriendRequest(data.id());
            }, new Identifier("msf_friends", "textures/gui/sprites/icon/cancel.png"), 12, 12)
                .dimensions(0, 0, 20, 20).build();
            children.add(revokeBtn);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            renderBase(context, y, x, entryWidth, entryHeight, hovered);
            revokeBtn.setX(x + entryWidth - 24); revokeBtn.setY(y + (entryHeight - 20) / 2);
            revokeBtn.render(context, mouseX, mouseY, tickDelta);
        }
    }
}
