package dev.msf.friends.screen;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.FriendJoinHandler;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.PresenceHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import dev.msf.friends.compat.GuiGraphicsExtractor;
import dev.msf.friends.compat.Identifier;
import dev.msf.friends.compat.PlayerFaceExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import dev.msf.friends.compat.PlayerFaceExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import dev.msf.friends.compat.Identifier;
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

/**
 * Native friends screen modeled after SocialInteractionsScreen.
 */
public class FriendsScreen extends Screen {
    private static final Component TITLE = tr("screen.msf_friends.friends.title");
    private static final ResourceLocation BACKGROUND_SPRITE = Identifier.withDefaultNamespace("social_interactions/background");
    private static final ResourceLocation SEARCH_SPRITE = Identifier.withDefaultNamespace("icon/search");
    private static final Component SEARCH_HINT = Component.translatable("screen.msf_friends.friends.search_hint");
    private static final Component TAB_FRIENDS = tr("screen.msf_friends.friends.tab.friends");
    private static final Component TAB_PENDING = tr("screen.msf_friends.friends.tab.pending");
    private static final Component BUTTON_ADD = tr("screen.msf_friends.friends.button.add");
    private static final Component EMPTY_FRIENDS = Component.translatable("screen.msf_friends.friends.empty.friends").withStyle(s -> s.withColor(0xAAAAAA));
    private static final Component EMPTY_PENDING = Component.translatable("screen.msf_friends.friends.empty.pending").withStyle(s -> s.withColor(0xAAAAAA));
    private static final Component EMPTY_FILTER = Component.translatable("screen.msf_friends.friends.empty.filter").withStyle(s -> s.withColor(0xAAAAAA));
    private static final int BG_WIDTH = 236;
    private static final int ITEM_HEIGHT = 36;
    static final int SKIN_SIZE = 24;
    static final int PLAYERNAME_COLOR = 0xFFFFFFFF;

    private final Screen parent;
    private Page page = Page.FRIENDS;
    private @Nullable FriendsPlayerList playerList;
    private @Nullable EditBox searchBox;
    private @Nullable Button friendsButton;
    private @Nullable Button pendingButton;
    private @Nullable AbstractButton addButton;
    private @Nullable Runnable updateListener;
    private @Nullable Runnable presenceListener;
    private @Nullable Component statusMessage;
    private int statusColor = PLAYERNAME_COLOR;
    private final Map<UUID, PlayerSkin> resolvedSkins = new ConcurrentHashMap<>();
    private final Set<UUID> requestedSkins = ConcurrentHashMap.newKeySet();

    public FriendsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
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
        // Player list (full screen width, like SocialInteractionsScreen)
        this.playerList = new FriendsPlayerList(this.minecraft, this.width, this.listBottom() - this.listTop(), this.listTop(), ITEM_HEIGHT);
        this.playerList.updateSizeAndPosition(this.width, this.listBottom() - this.listTop(), this.listTop());

        // Tabs �?span the full card width (BG_WIDTH=236, each tab=118)
        int tabLeft = this.marginX() + 3;
        int tabW = BG_WIDTH / 2; // 118
        this.friendsButton = this.addRenderableWidget(
                Button.builder(TAB_FRIENDS, b -> showPage(Page.FRIENDS))
                        .bounds(tabLeft, 45, tabW, 20).build());
        this.pendingButton = this.addRenderableWidget(
                Button.builder(TAB_PENDING, b -> showPage(Page.PENDING))
                        .bounds(tabLeft + tabW, 45, tabW, 20).build());

        // Search box
        String oldSearch = this.searchBox != null ? this.searchBox.getValue() : "";
        this.searchBox = this.addRenderableWidget(
            new EditBox(this.font, this.marginX() + 28, 72, 152, 20, SEARCH_HINT));
        this.searchBox.setMaxLength(36);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(-1);
        this.searchBox.setValue(oldSearch);
        this.searchBox.setHint(SEARCH_HINT);
        this.searchBox.setResponder(value -> refreshLists());

        this.addButton = this.addRenderableWidget(
            SpriteIconButton.builder(BUTTON_ADD, b -> submitFriendRequestFromSearch(), true)
                .sprite(Identifier.fromNamespaceAndPath("minecraft", "icon/invite"), 12, 12)
                .width(20).build());
        this.addButton.setPosition(this.marginX() + 184, 72);

        // Player list (addWidget, not addRenderableWidget �?we render it manually)
        this.addWidget(this.playerList);

        // Done button
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.doneY(), 200, 20).build());

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
        if (this.searchBox != null && this.searchBox.isFocused()) {
            // Enter key confirmation
            if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER or GLFW_KEY_KP_ENTER
                submitFriendRequestFromSearch();
                return true;
            }
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

    private static void setButtonVisible(@Nullable AbstractButton button, boolean visible) {
        if (button == null) return;
        button.visible = visible;
        button.active = visible;
    }

    private void setStatus(Component message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void submitFriendRequestFromSearch() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.searchBox == null || this.minecraft == null) {
            setStatus(tr("message.msf_friends.service_not_ready"), 0xFFFF8080);
            return;
        }

        String value = this.searchBox.getValue().trim();
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
        action.whenComplete((result, error) -> this.minecraft.execute(() -> {
            if (error != null) {
                setStatus(tr("message.msf_friends.friend_request_send_failed", throwableMessage(error)), 0xFFFF8080);
            } else {
                applyResultMessage(tr("message.msf_friends.friend_request_sent"), result);
                if (result == FriendsService.ResultCode.SUCCESS) {
                    this.searchBox.setValue("");
                }
            }
            refreshLists();
        }));
    }

    private void applyResultMessage(Component successMessage, FriendsService.ResultCode result) {
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
        return this.searchBox != null && !this.searchBox.getValue().trim().isEmpty();
    }

    private boolean matchesFilter(String... values) {
        if (!hasFilter()) return true;
        String filter = this.searchBox != null ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
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
            playerList.msf$replaceEntries(entries);
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
            boolean canJoin = this.minecraft != null && this.minecraft.level == null;
            Map<UUID, String> joinRequests = (client.p2p() != null)
                    ? client.p2p().friendJoinHandler().incomingJoinRequestsView()
                    : Map.of();
            for (var f : friends) {
                if (!matchesFilter(f.name(), f.id().toString())) continue;
                Supplier<PlayerSkin> skinGetter = getSkinGetter(f.id(), f.name());
                entries.add(new FriendEntry(this, f, skinGetter, presence, hosting, canJoin, joinRequests));
            }
        } else {
            // Incoming friend requests
            for (var r : social.getIncomingRequests()) {
                if (!matchesFilter(r.name(), r.id().toString())) continue;
                Supplier<PlayerSkin> skinGetter = getSkinGetter(r.id(), r.name());
                entries.add(new IncomingEntry(this, r, skinGetter));
            }
            // Outgoing friend requests
            for (var r : social.getOutgoingRequests()) {
                if (!matchesFilter(r.name(), r.id().toString())) continue;
                Supplier<PlayerSkin> skinGetter = getSkinGetter(r.id(), r.name());
                entries.add(new OutgoingEntry(this, r, skinGetter));
            }
        }

        playerList.msf$replaceEntries(entries);
    }

    private Supplier<PlayerSkin> getSkinGetter(UUID profileId, String name) {
        return () -> {
            PlayerSkin skin = this.resolvedSkins.get(profileId);
            if (skin != null) return skin;

            Minecraft minecraft = this.minecraft;
            if (minecraft != null && this.requestedSkins.add(profileId)) {
                PlayerSkinResolver.fetchSkin(minecraft, profileId, name)
                        .thenAccept(resolved -> this.resolvedSkins.put(profileId, resolved));
            }

            return DefaultPlayerSkin.get(profileId);
        };
    }

    private Supplier<PlayerSkin> getSkinGetterByPmid(PresenceHandler presence, UUID pmid) {
        UUID profileId = presence.getProfileIdFromPmid(pmid);
        if (profileId != null) return getSkinGetter(profileId, "");
        return getSkinGetter(pmid, "");
    }

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
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.renderBackground(graphics, mouseX, mouseY, a);
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(graphics);
        int mx = this.marginX() + 3;
        g.blitSprite(BACKGROUND_SPRITE, mx, 64, BG_WIDTH, this.windowHeight());
        g.blitSprite(SEARCH_SPRITE, mx + 10, 76, 12, 12);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(graphics);
        // Dim the unselected tab to make the selected one visually stand out
        Button unselTab = (page == Page.FRIENDS) ? pendingButton : friendsButton;
        if (unselTab != null) {
            g.fill(unselTab.getX(), unselTab.getY(),
                    unselTab.getX() + unselTab.getWidth(),
                    unselTab.getY() + unselTab.getHeight(), 0x99000000);
        }
        // Title
        g.centeredText(this.font, TITLE, this.width / 2, 8, -1);
        if (this.statusMessage != null) {
            g.centeredText(this.font, this.statusMessage, this.width / 2, 24, this.statusColor);
        }
        // List or empty message
        if (playerList != null && !playerList.children().isEmpty()) {
            playerList.render(graphics, mouseX, mouseY, a);
        } else {
            Component empty = hasFilter() ? EMPTY_FILTER : (page == Page.FRIENDS ? EMPTY_FRIENDS : EMPTY_PENDING);
            g.centeredText(this.font, empty, this.width / 2, (this.listTop() + this.listBottom()) / 2, -1);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    enum Page { FRIENDS, PENDING }

    // ============ Player List (same pattern as SocialInteractionsPlayerList) ============

    static class FriendsPlayerList extends ContainerObjectSelectionList<BaseEntry> {
        public FriendsPlayerList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        /** Expose protected replaceEntries for our screen */
        void msf$replaceEntries(List<BaseEntry> entries) {
            replaceEntries(entries);
        }

        @Override
        protected void renderListBackground(GuiGraphics g) {
            // No background �?we draw our own
        }

        @Override
        protected void renderListSeparators(GuiGraphics g) {
            // No separators
        }

        @Override
        public int getRowWidth() {
            return 200;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + (this.width + this.getRowWidth()) / 2 + 4;
        }
    }

    // ============ Entry base (same pattern as PlayerEntry) ============

    static abstract class BaseEntry extends ContainerObjectSelectionList.Entry<BaseEntry> {
        protected final Minecraft minecraft = Minecraft.getInstance();
        protected final FriendsScreen screen;
        protected final String playerName;
        protected final Supplier<PlayerSkin> skinGetter;
        protected final List<net.minecraft.client.gui.components.AbstractWidget> children = new ArrayList<>();

        BaseEntry(FriendsScreen screen, String name, Supplier<PlayerSkin> skinGetter) {
            this.screen = screen;
            this.playerName = name;
            this.skinGetter = skinGetter;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return children;
        }

        /** Draw skin + name, return textStartX for subclass to add status or buttons */
        protected int renderBase(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, float delta) {
            GuiGraphicsExtractor ge = new GuiGraphicsExtractor(g);
            // Hover highlight
            if (mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height) {
                ge.fill(left, top, left + width, top + height, 0x22FFFFFF);
            }

            // Face — delegates to PlayerFaceRenderer.draw via compat layer
            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            PlayerFaceExtractor.extractRenderState(g, skinGetter.get(), skinX, skinY, SKIN_SIZE);

            // Name
            int textX = skinX + SKIN_SIZE + 4;
            ge.text(minecraft.font, playerName, textX, top + (height - minecraft.font.lineHeight) / 2, PLAYERNAME_COLOR);

            return textX;
        }
    }

    // ============ Friend entry (with status + buttons) ============

    static class FriendEntry extends BaseEntry {
        private final @Nullable Component status;
        private final int statusColor;
        private final AbstractButton removeBtn;
        private @Nullable AbstractButton actionBtn;   // invite / join / request (icon buttons)
        private @Nullable AbstractButton acceptBtn;   // accept incoming join request
        private @Nullable AbstractButton rejectBtn;   // reject incoming join request
        private @Nullable SpriteIconButton pendingLoadingBtn; // loading overlay while join request is pending

        FriendEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data,
                   Supplier<PlayerSkin> skinGetter, PresenceHandler presenceHandler,
                   boolean hosting, boolean canJoin, Map<UUID, String> incomingJoinRequests) {
            super(screen, data.name(), skinGetter);

            PresenceStatusDto pres = presenceHandler.getLatestPresence().presence().stream()
                    .filter(p -> p.profileId().equals(data.id())).findFirst().orElse(null);

            this.status = statusText(pres);
            this.statusColor = statusColor(pres);

            removeBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.remove_friend"), b -> {
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
            }, true)
                .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/remove"), 12, 12)
                .width(20).build();
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
                // 该好友发来了加入请求：在此处显示同意/拒绝
                actionBtn = null;
                pendingLoadingBtn = null;
                UUID reqPmid = friendPmid;
                AbstractButton ab = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.accept_join"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        c.p2p().acceptIncomingJoinRequest(reqPmid);
                        screen.refreshLists();
                    }
                }, true)
                    .sprite(Identifier.fromNamespaceAndPath("minecraft", "icon/checkmark"), 12, 12)
                    .width(20).build();
                children.add(ab);
                acceptBtn = ab;
                AbstractButton rb = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.reject_join"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        c.p2p().rejectIncomingJoinRequest(reqPmid);
                        screen.refreshLists();
                    }
                }, true)
                    .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/reject"), 12, 12)
                    .width(20).build();
                children.add(rb);
                rejectBtn = rb;
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin
                    && !presenceHandler.hasDismissedInvite(pres)
                    && pres.joinInfo() != null && pres.joinInfo().invited()) {
                // 房主已邀请我：接受邀�?= 走与「请求加入」相同的连接流程，但图标用勾
                actionBtn = null;
                UUID pmid = friendPmid;
                SpriteIconButton loadBtn2 = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.connecting"), b -> {}, false)
                    .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/loading"), 12, 12)
                    .width(20).build();
                loadBtn2.active = false;
                loadBtn2.visible = false;
                children.add(loadBtn2);
                pendingLoadingBtn = loadBtn2;
                AbstractButton ab = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.accept_invite"), b -> {
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
                }, true)
                    .sprite(Identifier.fromNamespaceAndPath("minecraft", "icon/checkmark"), 12, 12)
                    .width(20).build();
                children.add(ab);
                acceptBtn = ab;
                AbstractButton rb = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.reject_invite"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null) {
                        if (c.p2p() != null) c.p2p().declineInvite(pmid);
                        if (c.social() != null) c.social().getPresenceHandler().dismissInviteForPmid(pmid);
                        screen.refreshLists();
                    }
                }, true)
                    .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/reject"), 12, 12)
                    .width(20).build();
                children.add(rb);
                rejectBtn = rb;
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin) {
                // 好友正在托管，未受邀：发送申请，等对方同意后再显示连接遮�?                acceptBtn = null;
                rejectBtn = null;
                UUID pmid = friendPmid;
                SpriteIconButton loadBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.requesting"), b -> {}, false)
                    .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/loading"), 12, 12)
                    .width(20).build();
                loadBtn.active = false;
                loadBtn.visible = false;
                children.add(loadBtn);
                pendingLoadingBtn = loadBtn;
                actionBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.request_join"), b -> {
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
                }, true)
                    .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/join_request"), 12, 12)
                    .width(20).build();
                children.add(actionBtn);
            } else if (hosting && pres != null && "ONLINE".equals(pres.status().name())
                    && !presenceHandler.getInvitedPlayersBatch().contains(data.id())) {
                // 我是房主且好友在线：显示邀请按钮，点击后保持禁用状态直到列表自然刷�?                acceptBtn = null;
                rejectBtn = null;
                actionBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.invite"), b -> {
                    b.active = false;
                    presenceHandler.invitePlayer(data.id());
                }, true)
                    .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/join"), 12, 12)
                    .width(20).build();
                children.add(actionBtn);
                pendingLoadingBtn = null;
            } else {
                actionBtn = null;
                acceptBtn = null;
                rejectBtn = null;
                pendingLoadingBtn = null;
            }
        }

        static Component statusText(@Nullable PresenceStatusDto p) {
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
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            GuiGraphicsExtractor ge = new GuiGraphicsExtractor(g);
            // Hover highlight
            if (hovered) ge.fill(left, top, left + width, top + height, 0x22FFFFFF);

            // Face — delegates via compat layer
            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            PlayerFaceExtractor.extractRenderState(g, skinGetter.get(), skinX, skinY, SKIN_SIZE);

            // Name (top half)
            int textX = skinX + SKIN_SIZE + 4;
            int nameY = top + height / 3 - minecraft.font.lineHeight / 2;
            ge.text(minecraft.font, playerName, textX, nameY, PLAYERNAME_COLOR);

            // Status (bottom half)
            if (status != null) {
                ge.text(minecraft.font, status, textX, nameY + 12, statusColor);
            }

            // Buttons from right
            int btnX = left + width - 4;
            btnX -= 20;
            removeBtn.setX(btnX);
            removeBtn.setY(top + (height - 20) / 2);
            removeBtn.render(g, 0, 0, delta);

            if (acceptBtn != null && rejectBtn != null) {
                btnX -= 22;
                rejectBtn.setX(btnX); rejectBtn.setY(top + (height - 20) / 2);
                if (rejectBtn.visible) rejectBtn.render(g, 0, 0, delta);
                btnX -= 22;
                if (pendingLoadingBtn != null && pendingLoadingBtn.visible) {
                    pendingLoadingBtn.setX(btnX); pendingLoadingBtn.setY(top + (height - 20) / 2);
                    pendingLoadingBtn.render(g, 0, 0, delta);
                } else if (acceptBtn.visible) {
                    acceptBtn.setX(btnX); acceptBtn.setY(top + (height - 20) / 2);
                    acceptBtn.render(g, 0, 0, delta);
                }
            } else if (actionBtn != null) {
                btnX -= actionBtn.getWidth() + 2;
                int abx = btnX, aby = top + (height - 20) / 2;
                actionBtn.setX(abx); actionBtn.setY(aby);
                if (actionBtn.visible) actionBtn.render(g, 0, 0, delta);
                if (pendingLoadingBtn != null) {
                    pendingLoadingBtn.setX(abx); pendingLoadingBtn.setY(aby);
                    if (pendingLoadingBtn.visible) pendingLoadingBtn.render(g, 0, 0, delta);
                }
            }
        }
    }

    // ============ Incoming friend request ============

    static class IncomingEntry extends BaseEntry {
        private final AbstractButton acceptBtn, declineBtn;

        IncomingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<PlayerSkin> skinGetter) {
            super(screen, data.name(), skinGetter);

            acceptBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.accept_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) c.social().acceptIncomingFriendRequest(data.id());
            }, true)
                .sprite(Identifier.fromNamespaceAndPath("minecraft", "icon/checkmark"), 12, 12)
                .width(20).build();
            children.add(acceptBtn);

            declineBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.decline_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) c.social().declineIncomingFriendRequest(data.id());
            }, true)
                .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/reject"), 12, 12)
                .width(20).build();
            children.add(declineBtn);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderBase(g, index, top, left, width, height, mouseX, mouseY, delta);

            int btnX = left + width - 4;
            btnX -= 20;
            declineBtn.setX(btnX); declineBtn.setY(top + (height - 20) / 2);
            declineBtn.render(g, 0, 0, delta);
            btnX -= 22;
            acceptBtn.setX(btnX); acceptBtn.setY(top + (height - 20) / 2);
            acceptBtn.render(g, 0, 0, delta);
        }
    }

    // ============ Outgoing friend request ============

    static class OutgoingEntry extends BaseEntry {
        private final AbstractButton revokeBtn;

        OutgoingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<PlayerSkin> skinGetter) {
            super(screen, data.name(), skinGetter);

            revokeBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.revoke_friend_request"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) c.social().revokeOutgoingFriendRequest(data.id());
            }, true)
                .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/cancel"), 12, 12)
                .width(20).build();
            children.add(revokeBtn);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderBase(g, index, top, left, width, height, mouseX, mouseY, delta);
            revokeBtn.setX(left + width - 24); revokeBtn.setY(top + (height - 20) / 2);
            revokeBtn.render(g, 0, 0, delta);
        }
    }

    // ============ Incoming P2P join request ============

    static class JoinRequestEntry extends BaseEntry {
        private final AbstractButton acceptBtn, rejectBtn;

        JoinRequestEntry(FriendsScreen screen, String name, UUID fromPmid, Supplier<PlayerSkin> skinGetter) {
            super(screen, name, skinGetter);

            acceptBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.accept_join"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.p2p() != null) { c.p2p().acceptIncomingJoinRequest(fromPmid); screen.refreshLists(); }
            }, true)
                .sprite(Identifier.fromNamespaceAndPath("minecraft", "icon/checkmark"), 12, 12)
                .width(20).build();
            children.add(acceptBtn);

            rejectBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.reject_join"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.p2p() != null) { c.p2p().rejectIncomingJoinRequest(fromPmid); screen.refreshLists(); }
            }, true)
                .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/reject"), 12, 12)
                .width(20).build();
            children.add(rejectBtn);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderBase(g, index, top, left, width, height, mouseX, mouseY, delta);

            int btnX = left + width - 4;
            btnX -= 20;
            rejectBtn.setX(btnX); rejectBtn.setY(top + (height - 20) / 2);
            rejectBtn.render(g, 0, 0, delta);
            btnX -= 22;
            acceptBtn.setX(btnX); acceptBtn.setY(top + (height - 20) / 2);
            acceptBtn.render(g, 0, 0, delta);
        }
    }

    // ============ Friend invite ============

    static class InviteEntry extends BaseEntry {
        private final AbstractButton joinBtn;
        private final AbstractButton ignoreBtn;

        InviteEntry(FriendsScreen screen, String name, UUID pmid, Supplier<PlayerSkin> skinGetter) {
            super(screen, name, skinGetter);

            joinBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.join"), b -> {
                P2PConnectScreen.show(screen, pmid.toString());
            }, true)
                .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/join"), 12, 12)
                .width(20).build();
            children.add(joinBtn);

            ignoreBtn = SpriteIconButton.builder(tr("screen.msf_friends.friends.button.ignore_invite"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) { c.social().getPresenceHandler().dismissInviteForPmid(pmid); screen.refreshLists(); }
            }, true)
                .sprite(Identifier.fromNamespaceAndPath("msf_friends", "icon/cancel"), 12, 12)
                .width(20).build();
            children.add(ignoreBtn);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderBase(g, index, top, left, width, height, mouseX, mouseY, delta);

            int btnX = left + width - 4;
            btnX -= 20;
            ignoreBtn.setX(btnX); ignoreBtn.setY(top + (height - 20) / 2);
            ignoreBtn.render(g, 0, 0, delta);
            btnX -= 22;
            joinBtn.setX(btnX); joinBtn.setY(top + (height - 20) / 2);
            joinBtn.render(g, 0, 0, delta);
        }
    }
}

