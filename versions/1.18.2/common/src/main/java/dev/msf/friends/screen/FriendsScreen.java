package dev.msf.friends.screen;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.p2p.FriendJoinHandler;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.PresenceHandler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native friends screen for MC 1.18.2, adapted from 26.1.2's FriendsScreen.
 * 1.18.2 API: PoseStack, TextComponent, ObjectSelectionList(Minecraft,int,int,int,int,int),
 * EditBox.setSuggestion(String), Button(x,y,w,h,text,pressAction), ResourceLocation for skins.
 */
public class FriendsScreen extends Screen {
    private static final Component TITLE = tr("screen.msf_friends.friends.title");
    // 1.18.2 has textures/gui/social_interactions.png (not background.png)
    private static final ResourceLocation BACKGROUND_LOCATION = new ResourceLocation("textures/gui/social_interactions.png");
    // 1.18.2 does not have textures/gui/icon/search.png, so we skip the search icon
    private static final Component SEARCH_HINT = new TranslatableComponent("screen.msf_friends.friends.search_hint");
    private static final Component TAB_FRIENDS = tr("screen.msf_friends.friends.tab.friends");
    private static final Component TAB_PENDING = tr("screen.msf_friends.friends.tab.pending");
    private static final Component BUTTON_ADD = tr("screen.msf_friends.friends.button.add");
    private static final Component EMPTY_FRIENDS = new TranslatableComponent("screen.msf_friends.friends.empty.friends");
    private static final Component EMPTY_PENDING = new TranslatableComponent("screen.msf_friends.friends.empty.pending");
    private static final Component EMPTY_FILTER = new TranslatableComponent("screen.msf_friends.friends.empty.filter");
    private static final int BG_WIDTH = 236;
    private static final int BG_TEXTURE_WIDTH = 256;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int ITEM_HEIGHT = 36;
    static final int SKIN_SIZE = 24;
    static final int PLAYERNAME_COLOR = 0xFFFFFF;

    private final Screen parent;
    private Page page = Page.FRIENDS;
    private @Nullable FriendsPlayerList playerList;
    private @Nullable EditBox searchBox;
    private @Nullable Button friendsButton;
    private @Nullable Button pendingButton;
    private @Nullable Button addButton;
    private @Nullable Runnable updateListener;
    private @Nullable Runnable presenceListener;
    private @Nullable Component statusMessage;
    private int statusColor = PLAYERNAME_COLOR;
    private final Set<UUID> requestedSkins = ConcurrentHashMap.newKeySet();

    public FriendsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    private static Component tr(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    private int windowHeight() { return Math.max(96, panelBottom() - 64); }
    private int listTop()     { return 96; }
    private int doneY()       { return this.height - 30; }
    private int panelBottom() { return this.doneY() - 8; }
    private int listBottom()  { return this.panelBottom() - 8; }
    private int marginX()     { return (this.width - 238) / 2; }

    @Override
    protected void init() {
        // 1.18.2 ObjectSelectionList constructor: (Minecraft, width, height, y0, y1, itemHeight)
        this.playerList = new FriendsPlayerList(this.minecraft, this.width, this.listBottom() - this.listTop(), this.listTop(), this.listBottom(), ITEM_HEIGHT);

        int tabLeft = this.marginX() + 3;
        int tabW = BG_WIDTH / 2;
        this.friendsButton = this.addRenderableWidget(new Button(tabLeft, 45, tabW, 20, TAB_FRIENDS, b -> showPage(Page.FRIENDS)));
        this.pendingButton = this.addRenderableWidget(new Button(tabLeft + tabW, 45, tabW, 20, TAB_PENDING, b -> showPage(Page.PENDING)));

        String oldSearch = this.searchBox != null ? this.searchBox.getValue() : "";
        this.searchBox = new EditBox(this.font, this.marginX() + 28, 72, 152, 20, SEARCH_HINT);
        this.searchBox.setMaxLength(36);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(-1);
        this.searchBox.setValue(oldSearch);
        this.searchBox.setSuggestion(SEARCH_HINT.getString());
        this.searchBox.setResponder(value -> {
            searchBox.setSuggestion(value.isEmpty() ? SEARCH_HINT.getString() : null);
            refreshLists();
        });
        this.addRenderableWidget(this.searchBox);

        this.addButton = this.addRenderableWidget(new Button(this.marginX() + 184, 72, 20, 20, BUTTON_ADD, b -> submitFriendRequestFromSearch()));

        this.addWidget(this.playerList);

        this.addRenderableWidget(new Button(this.width / 2 - 100, this.doneY(), 200, 20, CommonComponents.GUI_DONE, b -> this.onClose()));

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
            if (client != null && client.social() != null) client.social().removeFriendListUpdateListener(updateListener);
            updateListener = null;
        }
        if (presenceListener != null) {
            var client = MsfFriendsBoot.get();
            if (client != null && client.social() != null) client.social().getPresenceHandler().removePresenceListener(presenceListener);
            presenceListener = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
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
        if (this.searchBox != null) { this.searchBox.setVisible(true); this.searchBox.setEditable(true); }
        if (this.addButton != null) { this.addButton.visible = true; this.addButton.active = true; }
        refreshLists();
    }

    private void setStatus(Component message, int color) { this.statusMessage = message; this.statusColor = color; }

    private void submitFriendRequestFromSearch() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.searchBox == null || this.minecraft == null) {
            setStatus(tr("message.msf_friends.service_not_ready"), 0xFFFF8080); return;
        }
        String value = this.searchBox.getValue().trim();
        if (value.isEmpty()) { setStatus(tr("message.msf_friends.enter_player_or_uuid"), 0xFFFFAA00); return; }

        CompletableFuture<FriendsService.ResultCode> action;
        try {
            action = looksLikeUuid(value) ? client.social().sendFriendRequest(UUID.fromString(value)) : client.social().sendFriendRequest(value);
        } catch (IllegalArgumentException ex) { setStatus(tr("message.msf_friends.invalid_uuid"), 0xFFFF8080); return; }

        setStatus(tr("message.msf_friends.sending_friend_request"), 0xFFE0E0E0);
        action.whenComplete((result, error) -> this.minecraft.execute(() -> {
            if (error != null) {
                setStatus(tr("message.msf_friends.friend_request_send_failed", throwableMessage(error)), 0xFFFF8080);
            } else {
                applyResultMessage(tr("message.msf_friends.friend_request_sent"), result);
                if (result == FriendsService.ResultCode.SUCCESS) this.searchBox.setValue("");
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

    private boolean looksLikeUuid(String value) { return value.length() == 36 && value.indexOf('-') >= 0; }

    private static String throwableMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private boolean hasFilter() { return this.searchBox != null && !this.searchBox.getValue().trim().isEmpty(); }

    private boolean matchesFilter(String... values) {
        if (!hasFilter()) return true;
        String filter = this.searchBox != null ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(filter)) return true;
        }
        return false;
    }

    void refreshLists() {
        if (playerList == null) return;
        List<BaseEntry> entries = new ArrayList<>();
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) { playerList.replaceEntries(entries); return; }

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
            Map<UUID, String> joinRequests = (client.p2p() != null) ? client.p2p().friendJoinHandler().incomingJoinRequestsView() : Map.of();
            for (var f : friends) {
                if (!matchesFilter(f.name(), f.id().toString())) continue;
                entries.add(new FriendEntry(this, f, f.id(), f.name(), presence, hosting, canJoin, joinRequests));
            }
        } else {
            for (var r : social.getIncomingRequests()) {
                if (!matchesFilter(r.name(), r.id().toString())) continue;
                entries.add(new IncomingEntry(this, r, r.id(), r.name()));
            }
            for (var r : social.getOutgoingRequests()) {
                if (!matchesFilter(r.name(), r.id().toString())) continue;
                entries.add(new OutgoingEntry(this, r, r.id(), r.name()));
            }
        }
        playerList.replaceEntries(entries);
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }

    @Override
    public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
        int mx = this.marginX() + 3;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, BACKGROUND_LOCATION);
        blit(matrices, mx, 64, 0, 0, BG_WIDTH, this.windowHeight(), BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);

        this.renderBackground(matrices);
        // Dim the unselected tab
        Button unselTab = (page == Page.FRIENDS) ? pendingButton : friendsButton;
        if (unselTab != null) fill(matrices, unselTab.x, unselTab.y, unselTab.x + unselTab.getWidth(), unselTab.y + unselTab.getHeight(), 0x99000000);

        drawCenteredString(matrices, this.font, TITLE, this.width / 2, 8, -1);
        if (this.statusMessage != null) drawCenteredString(matrices, this.font, this.statusMessage, this.width / 2, 24, this.statusColor);

        if (playerList != null && !playerList.children().isEmpty()) {
            playerList.render(matrices, mouseX, mouseY, delta);
        } else {
            Component empty = hasFilter() ? EMPTY_FILTER : (page == Page.FRIENDS ? EMPTY_FRIENDS : EMPTY_PENDING);
            drawCenteredString(matrices, this.font, empty, this.width / 2, (this.listTop() + this.listBottom()) / 2, 0xAAAAAA);
        }

        // Render widgets on top
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }

    enum Page { FRIENDS, PENDING }

    // ============ Player List ============
    static class FriendsPlayerList extends ObjectSelectionList<BaseEntry> {
        public FriendsPlayerList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }
        @Override public int getRowWidth() { return 200; }
        @Override protected int getScrollbarPosition() { return this.x0 + (this.width + this.getRowWidth()) / 2 + 4; }
        public void replaceEntries(List<BaseEntry> entries) {
            this.clearEntries();
            for (var e : entries) this.addEntry(e);
        }
    }

    // ============ Entry base ============
    static abstract class BaseEntry extends ObjectSelectionList.Entry<BaseEntry> {
        protected final Minecraft minecraft = Minecraft.getInstance();
        protected final FriendsScreen screen;
        protected final String playerName;
        protected final UUID profileId;

        BaseEntry(FriendsScreen screen, String name, UUID profileId) {
            this.screen = screen;
            this.playerName = name;
            this.profileId = profileId;
        }

        /** Request skin if not already done */
        protected void ensureSkinRequested() {
            if (this.minecraft != null && screen.requestedSkins.add(profileId)) {
                PlayerSkinResolver.fetchSkin(minecraft, profileId, playerName);
            }
        }

        /** Draw skin face, returns text start X */
        protected int renderBase(PoseStack matrices, int index, int top, int left, int width, int height, int mouseX, int mouseY, float delta, boolean hovered) {
            ensureSkinRequested();
            if (hovered) fill(matrices, left, top, left + width, top + height, 0x22FFFFFF);
            int skinX = left + 4, skinY = top + (height - SKIN_SIZE) / 2;
            renderFace(matrices, profileId, skinX, skinY, SKIN_SIZE);
            int textX = skinX + SKIN_SIZE + 4;
            this.minecraft.font.draw(matrices, playerName, textX, top + (height - this.minecraft.font.lineHeight) / 2, PLAYERNAME_COLOR);
            return textX;
        }

        protected void renderFace(PoseStack matrices, UUID id, int x, int y, int size) {
            ResourceLocation skinLoc = PlayerSkinResolver.getSkin(id);
            RenderSystem.setShaderTexture(0, skinLoc);
            blit(matrices, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
            blit(matrices, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
        }

        @Override
        public abstract Component getNarration();
    }

    // ============ Friend entry (with status + buttons) ============
    static class FriendEntry extends BaseEntry {
        private final @Nullable Component status;
        private final int statusColor;
        private final Button removeBtn;
        private final @Nullable Button actionBtn;
        private final @Nullable Button acceptBtn;
        private final @Nullable Button rejectBtn;
        private final @Nullable Button pendingLoadingBtn;

        FriendEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, UUID profileId, String name,
                   PresenceHandler presenceHandler, boolean hosting, boolean canJoin, Map<UUID, String> incomingJoinRequests) {
            super(screen, name, profileId);
            PresenceStatusDto pres = presenceHandler.getLatestPresence().presence().stream()
                    .filter(p -> p.profileId().equals(data.id())).findFirst().orElse(null);
            this.status = statusText(pres);
            this.statusColor = statusColor(pres);

            removeBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.remove_friend"), b -> {
                if (this.minecraft == null) return;
                final String friendName = data.name();
                this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                    this.minecraft.setScreen(screen);
                    if (confirmed) {
                        var c = MsfFriendsBoot.get();
                        if (c != null && c.social() != null) {
                            c.social().removeFriend(data.id()).whenComplete((result, err) -> {
                                if (result == FriendsService.ResultCode.SUCCESS) {
                                    this.minecraft.execute(() -> FriendToast.show(
                                        tr("screen.msf_friends.friends.toast.friend_removed.title"),
                                        tr("screen.msf_friends.friends.toast.friend_removed.description", friendName), data.id()));
                                }
                            });
                        }
                    }
                }, tr("screen.msf_friends.friends.confirm_remove.title"),
                   tr("screen.msf_friends.friends.confirm_remove.message", data.name())));
            });

            UUID friendPmid = pres != null ? pres.pmid() : null;
            boolean hasPendingJoinReq = hosting && friendPmid != null && incomingJoinRequests.containsKey(friendPmid);

            if (pres == null) {
                actionBtn = null; acceptBtn = null; rejectBtn = null; pendingLoadingBtn = null;
            } else if (hasPendingJoinReq) {
                actionBtn = null; pendingLoadingBtn = null;
                UUID reqPmid = friendPmid;
                acceptBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.accept_join"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) { c.p2p().acceptIncomingJoinRequest(reqPmid); screen.refreshLists(); }
                });
                rejectBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.reject_join"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) { c.p2p().rejectIncomingJoinRequest(reqPmid); screen.refreshLists(); }
                });
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin
                    && !presenceHandler.hasDismissedInvite(pres) && pres.joinInfo() != null && pres.joinInfo().invited()) {
                actionBtn = null;
                UUID pmid = friendPmid;
                Button loadBtn2 = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.connecting"), b -> {});
                loadBtn2.active = false; loadBtn2.visible = false;
                pendingLoadingBtn = loadBtn2;
                acceptBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.accept_invite"), b -> {
                    b.visible = false; loadBtn2.active = true; loadBtn2.visible = true;
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        var p2p = c.p2p();
                        Runnable[] lr = {null};
                        lr[0] = () -> {
                            var st = p2p.outgoingJoinState(pmid);
                            if (st == FriendJoinHandler.OutgoingJoinState.CONNECTING || st == FriendJoinHandler.OutgoingJoinState.CONNECTED) {
                                p2p.removeJoinStateListener(lr[0]); P2PConnectScreen.show(screen.parent, pmid.toString());
                            } else if (st == FriendJoinHandler.OutgoingJoinState.NONE) {
                                p2p.removeJoinStateListener(lr[0]); screen.refreshLists();
                            }
                        };
                        p2p.addJoinStateListener(lr[0]);
                        p2p.joinPlayer(pmid.toString()).exceptionally(err -> { p2p.removeJoinStateListener(lr[0]); screen.refreshLists(); return null; });
                    }
                });
                rejectBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.reject_invite"), b -> {
                    var c = MsfFriendsBoot.get();
                    if (c != null) {
                        if (c.p2p() != null) c.p2p().declineInvite(pmid);
                        if (c.social() != null) c.social().getPresenceHandler().dismissInviteForPmid(pmid);
                        screen.refreshLists();
                    }
                });
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin) {
                acceptBtn = null; rejectBtn = null;
                UUID pmid = friendPmid;
                Button loadBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.requesting"), b -> {});
                loadBtn.active = false; loadBtn.visible = false;
                pendingLoadingBtn = loadBtn;
                actionBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.request_join"), b -> {
                    b.visible = false; loadBtn.active = true; loadBtn.visible = true;
                    var c = MsfFriendsBoot.get();
                    if (c != null && c.p2p() != null) {
                        var p2p = c.p2p();
                        Runnable[] lr = {null};
                        lr[0] = () -> {
                            var st = p2p.outgoingJoinState(pmid);
                            if (st == FriendJoinHandler.OutgoingJoinState.CONNECTING || st == FriendJoinHandler.OutgoingJoinState.CONNECTED) {
                                p2p.removeJoinStateListener(lr[0]); P2PConnectScreen.show(screen.parent, pmid.toString());
                            } else if (st == FriendJoinHandler.OutgoingJoinState.NONE) {
                                p2p.removeJoinStateListener(lr[0]); screen.refreshLists();
                            }
                        };
                        p2p.addJoinStateListener(lr[0]);
                        p2p.joinPlayer(pmid.toString()).exceptionally(err -> { p2p.removeJoinStateListener(lr[0]); screen.refreshLists(); return null; });
                    }
                });
            } else if (hosting && "ONLINE".equals(pres.status().name()) && !presenceHandler.getInvitedPlayersBatch().contains(data.id())) {
                acceptBtn = null; rejectBtn = null; pendingLoadingBtn = null;
                actionBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.invite"), b -> { b.active = false; presenceHandler.invitePlayer(data.id()); });
            } else {
                actionBtn = null; acceptBtn = null; rejectBtn = null; pendingLoadingBtn = null;
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
        public void render(PoseStack matrices, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            ensureSkinRequested();
            if (hovered) fill(matrices, left, top, left + width, top + height, 0x22FFFFFF);

            int skinX = left + 4, skinY = top + (height - SKIN_SIZE) / 2;
            renderFace(matrices, profileId, skinX, skinY, SKIN_SIZE);
            int textX = skinX + SKIN_SIZE + 4;
            int nameY = top + height / 3 - minecraft.font.lineHeight / 2;
            minecraft.font.draw(matrices, playerName, textX, nameY, PLAYERNAME_COLOR);
            if (status != null) minecraft.font.draw(matrices, status, textX, nameY + 12, statusColor);

            int btnX = left + width - 4;
            btnX -= 20;
            removeBtn.x = btnX; removeBtn.y = top + (height - 20) / 2; removeBtn.render(matrices, 0, 0, delta);

            if (acceptBtn != null && rejectBtn != null) {
                btnX -= 22;
                rejectBtn.x = btnX; rejectBtn.y = top + (height - 20) / 2;
                if (rejectBtn.visible) rejectBtn.render(matrices, 0, 0, delta);
                btnX -= 22;
                if (pendingLoadingBtn != null && pendingLoadingBtn.visible) {
                    pendingLoadingBtn.x = btnX; pendingLoadingBtn.y = top + (height - 20) / 2;
                    pendingLoadingBtn.render(matrices, 0, 0, delta);
                } else if (acceptBtn.visible) {
                    acceptBtn.x = btnX; acceptBtn.y = top + (height - 20) / 2;
                    acceptBtn.render(matrices, 0, 0, delta);
                }
            } else if (actionBtn != null) {
                btnX -= actionBtn.getWidth() + 2;
                actionBtn.x = btnX; actionBtn.y = top + (height - 20) / 2;
                if (actionBtn.visible) actionBtn.render(matrices, 0, 0, delta);
                if (pendingLoadingBtn != null) {
                    pendingLoadingBtn.x = btnX; pendingLoadingBtn.y = top + (height - 20) / 2;
                    if (pendingLoadingBtn.visible) pendingLoadingBtn.render(matrices, 0, 0, delta);
                }
            }
        }

        @Override
        public Component getNarration() {
            return playerName != null ? new TextComponent(playerName) : TextComponent.EMPTY;
        }
    }

    // ============ Incoming friend request ============
    static class IncomingEntry extends BaseEntry {
        private final Button acceptBtn, declineBtn;

        IncomingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, UUID profileId, String name) {
            super(screen, name, profileId);
            acceptBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.accept_friend_request"), b -> {
                var c = MsfFriendsBoot.get(); if (c != null && c.social() != null) c.social().acceptIncomingFriendRequest(data.id());
            });
            declineBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.decline_friend_request"), b -> {
                var c = MsfFriendsBoot.get(); if (c != null && c.social() != null) c.social().declineIncomingFriendRequest(data.id());
            });
        }

        @Override
        public void render(PoseStack matrices, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderBase(matrices, index, top, left, width, height, mouseX, mouseY, delta, hovered);
            int btnX = left + width - 4;
            btnX -= 20; declineBtn.x = btnX; declineBtn.y = top + (height - 20) / 2; declineBtn.render(matrices, 0, 0, delta);
            btnX -= 22; acceptBtn.x = btnX; acceptBtn.y = top + (height - 20) / 2; acceptBtn.render(matrices, 0, 0, delta);
        }

        @Override
        public Component getNarration() { return playerName != null ? new TextComponent(playerName) : TextComponent.EMPTY; }
    }

    // ============ Outgoing friend request ============
    static class OutgoingEntry extends BaseEntry {
        private final Button revokeBtn;

        OutgoingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, UUID profileId, String name) {
            super(screen, name, profileId);
            revokeBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.revoke_friend_request"), b -> {
                var c = MsfFriendsBoot.get(); if (c != null && c.social() != null) c.social().revokeOutgoingFriendRequest(data.id());
            });
        }

        @Override
        public void render(PoseStack matrices, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderBase(matrices, index, top, left, width, height, mouseX, mouseY, delta, hovered);
            revokeBtn.x = left + width - 24; revokeBtn.y = top + (height - 20) / 2; revokeBtn.render(matrices, 0, 0, delta);
        }

        @Override
        public Component getNarration() { return playerName != null ? new TextComponent(playerName) : TextComponent.EMPTY; }
    }

    // ============ Incoming P2P join request (from non-friend) ============
    static class JoinRequestEntry extends BaseEntry {
        private final Button acceptBtn, rejectBtn;

        JoinRequestEntry(FriendsScreen screen, String name, UUID fromPmid) {
            super(screen, name, fromPmid);
            acceptBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.accept_join"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.p2p() != null) { c.p2p().acceptIncomingJoinRequest(fromPmid); screen.refreshLists(); }
            });
            rejectBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.reject_join"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.p2p() != null) { c.p2p().rejectIncomingJoinRequest(fromPmid); screen.refreshLists(); }
            });
        }

        @Override
        public void render(PoseStack matrices, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderBase(matrices, index, top, left, width, height, mouseX, mouseY, delta, hovered);
            int btnX = left + width - 4;
            btnX -= 20; rejectBtn.x = btnX; rejectBtn.y = top + (height - 20) / 2; rejectBtn.render(matrices, 0, 0, delta);
            btnX -= 22; acceptBtn.x = btnX; acceptBtn.y = top + (height - 20) / 2; acceptBtn.render(matrices, 0, 0, delta);
        }

        @Override
        public Component getNarration() { return playerName != null ? new TextComponent(playerName) : TextComponent.EMPTY; }
    }

    // ============ Friend invite (join a hosting friend) ============
    static class InviteEntry extends BaseEntry {
        private final Button joinBtn, ignoreBtn;

        InviteEntry(FriendsScreen screen, String name, UUID pmid) {
            super(screen, name, pmid);
            joinBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.join"), b -> {
                P2PConnectScreen.show(screen.parent, pmid.toString());
            });
            ignoreBtn = new Button(0, 0, 20, 20, tr("screen.msf_friends.friends.button.ignore_invite"), b -> {
                var c = MsfFriendsBoot.get();
                if (c != null && c.social() != null) { c.social().getPresenceHandler().dismissInviteForPmid(pmid); screen.refreshLists(); }
            });
        }

        @Override
        public void render(PoseStack matrices, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderBase(matrices, index, top, left, width, height, mouseX, mouseY, delta, hovered);
            int btnX = left + width - 4;
            btnX -= 20; ignoreBtn.x = btnX; ignoreBtn.y = top + (height - 20) / 2; ignoreBtn.render(matrices, 0, 0, delta);
            btnX -= 22; joinBtn.x = btnX; joinBtn.y = top + (height - 20) / 2; joinBtn.render(matrices, 0, 0, delta);
        }

        @Override
        public Component getNarration() { return playerName != null ? new TextComponent(playerName) : TextComponent.EMPTY; }
    }
}
