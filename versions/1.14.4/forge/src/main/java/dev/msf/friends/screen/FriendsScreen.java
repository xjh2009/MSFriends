package dev.msf.friends.screen;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.p2p.FriendJoinHandler;
import dev.msf.friends.p2p.P2PManager;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.PresenceHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.SlotGui;
import net.minecraft.client.gui.widget.list.AbstractList;
import net.minecraft.client.gui.widget.list.AbstractList.AbstractListEntry;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.ITextComponent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Native friends screen for MC 1.13.2 Forge (MCP mappings).
 *
 * <p>Key API differences from 1.14+:
 * <ul>
 *   <li>{@code Screen} → {@code Screen}</li>
 *   <li>{@code Button} → {@code Button} (Forge) or subclass with onClick()</li>
 *   <li>{@code AbstractList} → {@code SlotGui} (no generic entries, uses drawSlot)</li>
 *   <li>{@code ConfirmScreen} → {@code ConfirmScreen} (with BooleanConsumer)</li>
 *   <li>{@code TextFieldWidget} → {@code TextFieldWidget} (id + FontRenderer constructor)</li>
 *   <li>{@code AbstractGui.blit()} → {@code AbstractGui.blit()}</li>
 *   <li>{@code render()} for screen, {@code render(int,int,float)} for buttons</li>
 *   <li>{@code fill()} → {@code AbstractGui.fill()}</li>
 *   <li>{@code font} → {@code fontRenderer}</li>
 *   <li>{@code drawScreen} in SlotGui for list rendering</li>
 * </ul>
 */
public class FriendsScreen extends Screen implements BooleanConsumer {
    private static final ResourceLocation BACKGROUND_TEXTURE =
            new ResourceLocation("msf_friends", "textures/gui/social_interactions.png");
    private static final int BG_WIDTH = 236;
    private static final int ITEM_HEIGHT = 36;

    static final int SKIN_SIZE = 24;
    static final int PLAYERNAME_COLOR = 0xFFFFFFFF;
    static final ResourceLocation ICON_REMOVE = icon("remove");
    static final ResourceLocation ICON_ACCEPT = icon("join");
    static final ResourceLocation ICON_REJECT = icon("reject");
    static final ResourceLocation ICON_JOIN = icon("join_request");
    static final ResourceLocation ICON_INVITE = icon("join");
    static final ResourceLocation ICON_CANCEL = icon("cancel");
    static final ResourceLocation ICON_LOADING = icon("loading");

    private final Screen parent;
    private Page page = Page.FRIENDS;
    private @Nullable FriendsPlayerList playerList;
    private @Nullable TextFieldWidget searchBox;
    private @Nullable SimpleButton friendsButton;
    private @Nullable SimpleButton pendingButton;
    private @Nullable SimpleButton addButton;
    private @Nullable Runnable updateListener;
    private @Nullable Runnable presenceListener;
    private @Nullable String statusMessage;
    private int statusColor = PLAYERNAME_COLOR;
    private final Map<UUID, ResourceLocation> resolvedSkins = new ConcurrentHashMap<>();
    private final Set<UUID> requestedSkins = ConcurrentHashMap.newKeySet();
    // Pending confirm action for ConfirmScreen callback
    private @Nullable Runnable pendingConfirmAction;

    public FriendsScreen(Screen parent) {
        super(new TranslationTextComponent("screen.msf_friends.friends"));
        this.parent = parent;
    }

    private static String trStr(String key, Object... args) {
        return I18n.format(key, args);
    }

    private static ResourceLocation icon(String name) {
        return new ResourceLocation("msf_friends", "textures/gui/sprites/icon/" + name + ".png");
    }

    private int windowHeight() { return Math.max(96, panelBottom() - 64); }
    private int listTop() { return 96; }
    private int doneY() { return this.height - 30; }
    private int panelBottom() { return this.doneY() - 8; }
    private int listBottom() { return this.panelBottom() - 8; }
    private int marginX() { return (this.width - 238) / 2; }

    @Override
    public void init(Minecraft mc, int w, int h) {
        super.init(mc, w, h);
        int listH = this.listBottom() - this.listTop();
        this.playerList = new FriendsPlayerList(this.minecraft, this.width, listH, this.listTop(), this.listBottom(), ITEM_HEIGHT);

        int tabLeft = this.marginX() + 3;
        int tabW = BG_WIDTH / 2;
        this.friendsButton = new SimpleButton(tabLeft, 45, tabW, 20,
                trStr("screen.msf_friends.friends.tab.friends"), () -> showPage(Page.FRIENDS));
        this.addButton(this.friendsButton);
        this.pendingButton = new SimpleButton(tabLeft + tabW, 45, tabW, 20,
                trStr("screen.msf_friends.friends.tab.pending"), () -> showPage(Page.PENDING));
        this.addButton(this.pendingButton);

        String oldSearch = this.searchBox != null ? this.searchBox.getText() : "";
        this.searchBox = new TextFieldWidget(this.font, this.marginX() + 28, 72, 152, 20, "");
        this.searchBox.setMaxStringLength(36);
        this.searchBox.setVisible(true);
        this.searchBox.setEnabled(true);
        this.searchBox.setText(oldSearch);
        // setTextAcceptHandler not available in 1.14.4, use setResponder instead
        // this.searchBox.setResponder(value -> refreshLists());

        this.addButton = new SimpleButton(this.marginX() + 184, 72, 40, 20,
                trStr("screen.msf_friends.friends.button.add"), this::submitFriendRequestFromSearch);
        this.addButton(this.addButton);

        this.children.add(this.playerList);

        this.addButton(new SimpleButton(this.width / 2 - 100, this.doneY(), 200, 20,
                trStr("gui.done"), this::close));

        MsfFriendsBoot client = MsfFriendsBoot.get();
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
    public void onClose() {
        super.onClose();
        if (updateListener != null) {
            MsfFriendsBoot client = MsfFriendsBoot.get();
            if (client != null && client.social() != null) {
                client.social().removeFriendListUpdateListener(updateListener);
            }
            updateListener = null;
        }
        if (presenceListener != null) {
            MsfFriendsBoot client = MsfFriendsBoot.get();
            if (client != null && client.social() != null) {
                client.social().getPresenceHandler().removePresenceListener(presenceListener);
            }
            presenceListener = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()
                && (keyCode == 257 || keyCode == 335)) {
            submitFriendRequestFromSearch();
            return true;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.charTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // BooleanConsumer implementation
    @Override
    public void accept(boolean result) {
        if (result && pendingConfirmAction != null) {
            pendingConfirmAction.run();
        }
        pendingConfirmAction = null;
        this.minecraft.displayGuiScreen(this);
    }

    /** Sets the action to run when the next ConfirmScreen confirm fires. */
    void setPendingConfirm(Runnable action) {
        this.pendingConfirmAction = action;
    }

    private void showPage(Page page) {
        this.page = page;
        if (this.friendsButton != null) this.friendsButton.active = true;
        if (this.pendingButton != null) this.pendingButton.active = true;
        if (this.searchBox != null) {
            this.searchBox.setVisible(true);
            this.searchBox.setEnabled(true);
        }
        if (this.addButton != null) {
            this.addButton.visible = true;
            this.addButton.active = true;
        }
        refreshLists();
    }

    private void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void submitFriendRequestFromSearch() {
        MsfFriendsBoot client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.searchBox == null || this.minecraft == null) {
            setStatus(trStr("message.msf_friends.service_not_ready"), 0xFFFF8080);
            return;
        }

        String value = this.searchBox.getText().trim();
        if (value.isEmpty()) {
            setStatus(trStr("message.msf_friends.enter_player_or_uuid"), 0xFFFFAA00);
            return;
        }

        CompletableFuture<FriendsService.ResultCode> action;
        try {
            action = looksLikeUuid(value)
                    ? client.social().sendFriendRequest(UUID.fromString(value))
                    : client.social().sendFriendRequest(value);
        } catch (IllegalArgumentException ex) {
            setStatus(trStr("message.msf_friends.invalid_uuid"), 0xFFFF8080);
            return;
        }

        setStatus(trStr("message.msf_friends.sending_friend_request"), 0xFFE0E0E0);
        action.whenComplete((result, error) -> this.minecraft.enqueue(() -> {
            if (error != null) {
                setStatus(trStr("message.msf_friends.friend_request_send_failed", throwableMessage(error)), 0xFFFF8080);
            } else {
                applyResultMessage(trStr("message.msf_friends.friend_request_sent"), result);
                if (result == FriendsService.ResultCode.SUCCESS) {
                    this.searchBox.setText("");
                }
            }
            refreshLists();
        }));
    }

    private void applyResultMessage(String successMessage, FriendsService.ResultCode result) {
        if (result == FriendsService.ResultCode.SUCCESS) {
            setStatus(successMessage, 0xFF55FF55);
        } else if (result == FriendsService.ResultCode.TOO_MANY_REQUESTS) {
            setStatus(trStr("message.msf_friends.result.too_many_requests"), 0xFFFFAA00);
        } else if (result == FriendsService.ResultCode.UNKNOWN_PROFILE) {
            setStatus(trStr("message.msf_friends.result.unknown_profile"), 0xFFFF8080);
        } else if (result == FriendsService.ResultCode.FORBIDDEN) {
            setStatus(trStr("message.msf_friends.result.forbidden"), 0xFFFF8080);
        } else if (result == FriendsService.ResultCode.SERVICE_NOT_AVAILABLE
                || result == FriendsService.ResultCode.TEMPORARY_UNAVAILABLE) {
            setStatus(trStr("message.msf_friends.result.service_not_available"), 0xFFFF8080);
        } else if (result == FriendsService.ResultCode.CONNECTION_ISSUE) {
            setStatus(trStr("message.msf_friends.result.connection_issue"), 0xFFFF8080);
        } else if (result == FriendsService.ResultCode.UPGRADE_NEEDED) {
            setStatus(trStr("message.msf_friends.result.upgrade_needed"), 0xFFFF8080);
        } else {
            setStatus(trStr("message.msf_friends.result.generic_error"), 0xFFFF8080);
        }
    }

    private boolean looksLikeUuid(String value) {
        return value.length() == 36 && value.indexOf('-') >= 0;
    }

    private static String throwableMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) { cause = cause.getCause(); }
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
        List<BaseEntry> entries = new ArrayList<BaseEntry>();

        MsfFriendsBoot client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) {
            playerList.setEntries(entries);
            return;
        }

        PlayerSocialManager social = client.social();
        PresenceHandler presence = social.getPresenceHandler();
        boolean hosting = client.bridge() != null && client.bridge().isHostingP2P();

        if (page == Page.FRIENDS) {
            List<PresenceStatusDto> pList = presence.getLatestPresence().presence();
            List<PlayerSocialManager.PlayerData> friends = new ArrayList<PlayerSocialManager.PlayerData>(social.getFriends());
            friends.sort((a, b) -> {
                boolean aOnline = false;
                boolean bOnline = false;
                for (PresenceStatusDto p : pList) {
                    if (p.profileId().equals(a.id())) aOnline = true;
                    if (p.profileId().equals(b.id())) bOnline = true;
                }
                if (aOnline != bOnline) return aOnline ? -1 : 1;
                return String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
            });
            boolean canJoin = this.minecraft != null && this.minecraft.field_71441_e == null;
            Map<UUID, String> joinRequests;
            if (client.p2p() != null) {
                joinRequests = client.p2p().friendJoinHandler().incomingJoinRequestsView();
            } else {
                joinRequests = Collections.emptyMap();
            }
            for (PlayerSocialManager.PlayerData f : friends) {
                if (!matchesFilter(f.name(), f.id().toString())) continue;
                Supplier<ResourceLocation> skinGetter = getSkinGetter(f.id(), f.name());
                entries.add(new FriendEntry(this, f, skinGetter, presence, hosting, canJoin, joinRequests));
            }
        } else {
            for (PlayerSocialManager.PlayerData r : social.getIncomingRequests()) {
                if (!matchesFilter(r.name(), r.id().toString())) continue;
                Supplier<ResourceLocation> skinGetter = getSkinGetter(r.id(), r.name());
                entries.add(new IncomingEntry(this, r, skinGetter));
            }
            for (PlayerSocialManager.PlayerData r : social.getOutgoingRequests()) {
                if (!matchesFilter(r.name(), r.id().toString())) continue;
                Supplier<ResourceLocation> skinGetter = getSkinGetter(r.id(), r.name());
                entries.add(new OutgoingEntry(this, r, skinGetter));
            }
        }

        playerList.setEntries(entries);
    }

    private Supplier<ResourceLocation> getSkinGetter(UUID profileId, String name) {
        return () -> {
            ResourceLocation skin = this.resolvedSkins.get(profileId);
            if (skin != null) return skin;
            if (this.minecraft != null && this.requestedSkins.add(profileId)) {
                PlayerSkinResolver.fetchSkin(this.minecraft, profileId, name)
                        .thenAccept(resolved -> this.resolvedSkins.put(profileId, resolved));
            }
            return DefaultPlayerSkin.getDefaultSkin(profileId);
        };
    }

    /** Called when "Done" button is pressed. */
    public void close() {
        this.minecraft.displayGuiScreen(parent);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        int mx = this.marginX() + 3;
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getInstance().getTextureManager().bindTexture(BACKGROUND_TEXTURE);
        AbstractGui.blit(mx, 64, BG_WIDTH, this.windowHeight(),
                0, 0, BG_WIDTH, this.windowHeight(), 256, 256);

        this.renderBackground();
        SimpleButton unselectedTab = (page == Page.FRIENDS) ? pendingButton : friendsButton;
        if (unselectedTab != null) {
            AbstractGui.fill(unselectedTab.x, unselectedTab.y,
                    unselectedTab.x + unselectedTab.getWidth(), unselectedTab.y + unselectedTab.getHeight(), 0x99000000);
        }
        this.drawCenteredString(this.font, trStr("screen.msf_friends.friends.title"), this.width / 2, 8, -1);
        if (this.statusMessage != null) {
            this.drawCenteredString(this.font, statusMessage, this.width / 2, 24, this.statusColor);
        }
        if (playerList != null && playerList.children().size() > 0) {
            playerList.render(mouseX, mouseY, delta);
        } else {
            String empty = hasFilter()
                    ? trStr("screen.msf_friends.friends.empty.filter")
                    : (page == Page.FRIENDS
                        ? trStr("screen.msf_friends.friends.empty.friends")
                        : trStr("screen.msf_friends.friends.empty.pending"));
            this.drawCenteredString(this.font, empty, this.width / 2,
                    (this.listTop() + this.listBottom()) / 2, 0xAAAAAA);
        }
        super.render(mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.searchBox != null) this.searchBox.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    enum Page { FRIENDS, PENDING }

    // ============ Player List (SlotGui-based) ============

    static class FriendsPlayerList extends AbstractList<BaseEntry> {

        public FriendsPlayerList(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        public void setEntries(List<BaseEntry> newEntries) {
            this.replaceEntries(newEntries);
        }

        @Override
        public int getRowWidth() { return 200; }

        @Override
        protected int getScrollbarPosition() { return this.x0 + this.width / 2 + 100; }

        @Override
        protected boolean isSelectedItem(int index) { return false; }

        public int itemCount() { return getItemCount(); }
    }

    // ============ Entry base ============

    static abstract class BaseEntry extends AbstractList.AbstractListEntry<BaseEntry> {
        protected final Minecraft minecraft = Minecraft.getInstance();
        protected final FriendsScreen screen;
        protected final String playerName;
        protected final Supplier<ResourceLocation> skinGetter;

        BaseEntry(FriendsScreen screen, String name, Supplier<ResourceLocation> skinGetter) {
            this.screen = screen;
            this.playerName = name;
            this.skinGetter = skinGetter;
        }

        @Override
        public void render(int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float delta) {
            if (isMouseOver) {
                AbstractGui.fill(left, top, left + width, top + height, 0x22FFFFFF);
            }
            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            ResourceLocation skin = skinGetter.get();
            renderFace(skin, skinX, skinY);
            int textX = skinX + SKIN_SIZE + 4;
            int nameY = top + (height - 9) / 2;
            minecraft.field_71466_p.drawString(playerName, textX, nameY, PLAYERNAME_COLOR);
        }

        protected void renderFace(ResourceLocation skinLoc, int x, int y) {
            Minecraft.getInstance().getTextureManager().bindTexture(skinLoc);
            GlStateManager.color4f(1f, 1f, 1f, 1f);
            GlStateManager.enableBlend();
            AbstractGui.blit(x, y, 8, 8, 8, 8, SKIN_SIZE, SKIN_SIZE, 64, 64);
            AbstractGui.blit(x, y, 40, 8, 8, 8, SKIN_SIZE, SKIN_SIZE, 64, 64);
            GlStateManager.disableBlend();
        }
    }

    // ============ Friend entry ============

    static class FriendEntry extends BaseEntry {
        private final @Nullable String status;
        private final int statusColor;
        private final IconButtonWidget removeBtn;
        private final @Nullable IconButtonWidget actionBtn;
        private final @Nullable IconButtonWidget acceptBtn;
        private final @Nullable IconButtonWidget rejectBtn;
        private final @Nullable IconButtonWidget pendingLoadingBtn;

        FriendEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data,
                   Supplier<ResourceLocation> skinGetter, PresenceHandler presenceHandler,
                   boolean hosting, boolean canJoin, Map<UUID, String> incomingJoinRequests) {
            super(screen, data.name(), skinGetter);

            PresenceStatusDto pres = null;
            for (PresenceStatusDto p : presenceHandler.getLatestPresence().presence()) {
                if (p.profileId().equals(data.id())) {
                    pres = p;
                    break;
                }
            }

            this.status = statusText(pres);
            this.statusColor = statusColor(pres);

            removeBtn = new IconButtonWidget(0, 0, 20, 20, trStr("screen.msf_friends.friends.button.remove_friend"),
                    b -> {
                        if (this.minecraft == null) return;
                        final String friendName = data.name();
                        screen.setPendingConfirm(() -> {
                            MsfFriendsBoot c = MsfFriendsBoot.get();
                            if (c != null && c.social() != null) {
                                c.social().removeFriend(data.id()).whenComplete((r, err) -> {
                                    if (r == FriendsService.ResultCode.SUCCESS) {
                                        this.minecraft.enqueue(() -> FriendToast.show(
                                            new TranslationTextComponent("screen.msf_friends.friends.toast.friend_removed.title"),
                                            new TranslationTextComponent("screen.msf_friends.friends.toast.friend_removed.description", friendName),
                                            data.id()
                                        ));
                                    }
                                });
                            }
                        });
                        this.minecraft.displayGuiScreen(new ConfirmScreen(screen,
                                new StringTextComponent(trStr("screen.msf_friends.friends.confirm_remove.title")),
                                new StringTextComponent(trStr("screen.msf_friends.friends.confirm_remove.message", data.name()))));
                    },
                    ICON_REMOVE, 12, 12);

            UUID friendPmid = pres != null ? pres.pmid() : null;
            boolean hasPendingJoinReq = hosting && friendPmid != null
                    && incomingJoinRequests.containsKey(friendPmid);

            if (pres == null) {
                actionBtn = null; acceptBtn = null; rejectBtn = null; pendingLoadingBtn = null;
            } else if (hasPendingJoinReq) {
                actionBtn = null; pendingLoadingBtn = null;
                UUID reqPmid = friendPmid;
                acceptBtn = new IconButtonWidget(0, 0, 20, 20, trStr("screen.msf_friends.friends.button.accept_join"),
                        b -> {
                            MsfFriendsBoot c = MsfFriendsBoot.get();
                            if (c != null && c.p2p() != null) {
                                c.p2p().acceptIncomingJoinRequest(reqPmid);
                                screen.refreshLists();
                            }
                        }, ICON_ACCEPT, 12, 12);
                rejectBtn = new IconButtonWidget(0, 0, 20, 20, trStr("screen.msf_friends.friends.button.reject_join"),
                        b -> {
                            MsfFriendsBoot c = MsfFriendsBoot.get();
                            if (c != null && c.p2p() != null) {
                                c.p2p().rejectIncomingJoinRequest(reqPmid);
                                screen.refreshLists();
                            }
                        }, ICON_REJECT, 12, 12);
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin
                    && !presenceHandler.hasDismissedInvite(pres)
                    && pres.joinInfo() != null && pres.joinInfo().invited()) {
                actionBtn = null;
                UUID pmid = friendPmid;
                IconButtonWidget loadBtn2 = new IconButtonWidget(0, 0, 20, 20,
                        trStr("screen.msf_friends.friends.button.connecting"), b -> {}, ICON_LOADING, 12, 12);
                loadBtn2.active = false; loadBtn2.visible = false;
                pendingLoadingBtn = loadBtn2;
                acceptBtn = new IconButtonWidget(0, 0, 20, 20, trStr("screen.msf_friends.friends.button.accept_invite"),
                        b -> {
                            b.visible = false; loadBtn2.active = true; loadBtn2.visible = true;
                            MsfFriendsBoot c = MsfFriendsBoot.get();
                            if (c != null && c.p2p() != null) {
                                P2PManager p2p = c.p2p();
                                Runnable[] lr = {null};
                                lr[0] = () -> {
                                    FriendJoinHandler.OutgoingJoinState st = p2p.outgoingJoinState(pmid);
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
                                        .exceptionally(err -> { p2p.removeJoinStateListener(lr[0]); screen.refreshLists(); return null; });
                            }
                        }, ICON_ACCEPT, 12, 12);
                rejectBtn = new IconButtonWidget(0, 0, 20, 20, trStr("screen.msf_friends.friends.button.reject_invite"),
                        b -> {
                            MsfFriendsBoot c = MsfFriendsBoot.get();
                            if (c != null) {
                                if (c.p2p() != null) c.p2p().declineInvite(pmid);
                                if (c.social() != null) c.social().getPresenceHandler().dismissInviteForPmid(pmid);
                                screen.refreshLists();
                            }
                        }, ICON_REJECT, 12, 12);
            } else if ("PLAYING_HOSTED_SERVER".equals(pres.status().name()) && friendPmid != null && canJoin) {
                acceptBtn = null; rejectBtn = null;
                UUID pmid = friendPmid;
                IconButtonWidget loadBtn = new IconButtonWidget(0, 0, 20, 20,
                        trStr("screen.msf_friends.friends.button.requesting"), b -> {}, ICON_LOADING, 12, 12);
                loadBtn.active = false; loadBtn.visible = false;
                pendingLoadingBtn = loadBtn;
                actionBtn = new IconButtonWidget(0, 0, 20, 20, trStr("screen.msf_friends.friends.button.request_join"),
                        b -> {
                            b.visible = false; loadBtn.active = true; loadBtn.visible = true;
                            MsfFriendsBoot c = MsfFriendsBoot.get();
                            if (c != null && c.p2p() != null) {
                                P2PManager p2p = c.p2p();
                                Runnable[] lr = {null};
                                lr[0] = () -> {
                                    FriendJoinHandler.OutgoingJoinState st = p2p.outgoingJoinState(pmid);
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
                                        .exceptionally(err -> { p2p.removeJoinStateListener(lr[0]); screen.refreshLists(); return null; });
                            }
                        }, ICON_JOIN, 12, 12);
            } else if (hosting && "ONLINE".equals(pres.status().name())
                    && !presenceHandler.getInvitedPlayersBatch().contains(data.id())) {
                acceptBtn = null; rejectBtn = null;
                actionBtn = new IconButtonWidget(0, 0, 20, 20, trStr("screen.msf_friends.friends.button.invite"),
                        b -> {
                            b.active = false; presenceHandler.invitePlayer(data.id());
                        }, ICON_INVITE, 12, 12);
                pendingLoadingBtn = null;
            } else {
                actionBtn = null; acceptBtn = null; rejectBtn = null; pendingLoadingBtn = null;
            }
        }

        static String statusText(@Nullable PresenceStatusDto p) {
            if (p == null) return trStr("screen.msf_friends.friends.presence.offline");
            String name = p.status().name();
            if ("ONLINE".equals(name)) return trStr("screen.msf_friends.friends.presence.online");
            if ("PLAYING_OFFLINE".equals(name)) return trStr("screen.msf_friends.friends.presence.playing_offline");
            if ("PLAYING_HOSTED_SERVER".equals(name)) return trStr("screen.msf_friends.friends.presence.hosting");
            if ("PLAYING_REALMS".equals(name)) return trStr("screen.msf_friends.friends.presence.realms");
            if ("PLAYING_SERVER".equals(name)) return trStr("screen.msf_friends.friends.presence.server");
            return trStr("screen.msf_friends.friends.presence.offline");
        }

        static int statusColor(@Nullable PresenceStatusDto p) {
            if (p == null) return 0xFFAAAAAA;
            String name = p.status().name();
            if ("ONLINE".equals(name)) return 0xFF55FF55;
            if ("PLAYING_OFFLINE".equals(name)) return 0xFFAAAAAA;
            if ("PLAYING_HOSTED_SERVER".equals(name)) return 0xFFFFAA00;
            if ("PLAYING_REALMS".equals(name)) return 0xFFFF55FF;
            if ("PLAYING_SERVER".equals(name)) return 0xFF55AAFF;
            return 0xFFAAAAAA;
        }

        @Override
        public void render(int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float delta) {
            boolean hovering = isMouseOver;
            if (hovering) AbstractGui.fill(left, top, left + width, top + height, 0x22FFFFFF);
            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            renderFace(skinGetter.get(), skinX, skinY);
            int textX = skinX + SKIN_SIZE + 4;
            int nameY = top + height / 3 - 9 / 2;
            minecraft.field_71466_p.drawString(playerName, textX, nameY, PLAYERNAME_COLOR);
            if (status != null) {
                minecraft.field_71466_p.drawString(status, textX, nameY + 12, statusColor);
            }
            int btnX = left + width - 4;
            btnX -= 22;
            removeBtn.x = btnX;
            removeBtn.y = top + (height - 20) / 2;
            removeBtn.render(mouseX, mouseY, delta);
            if (acceptBtn != null && rejectBtn != null) {
                btnX -= 22;
                rejectBtn.x = btnX;
                rejectBtn.y = top + (height - 20) / 2;
                rejectBtn.render(mouseX, mouseY, delta);
                btnX -= 22;
                acceptBtn.x = btnX;
                acceptBtn.y = top + (height - 20) / 2;
                acceptBtn.render(mouseX, mouseY, delta);
            } else if (actionBtn != null) {
                btnX -= 22;
                actionBtn.x = btnX;
                actionBtn.y = top + (height - 20) / 2;
                actionBtn.render(mouseX, mouseY, delta);
            }
            if (pendingLoadingBtn != null && pendingLoadingBtn.visible) {
                pendingLoadingBtn.x = left + width - 44;
                pendingLoadingBtn.y = top + (height - 20) / 2;
                pendingLoadingBtn.render(mouseX, mouseY, delta);
            }
        }
    }

    // ============ Incoming request entry ============

    static class IncomingEntry extends BaseEntry {
        private final PlayerSocialManager.PlayerData data;
        private final IconButtonWidget acceptBtn;
        private final IconButtonWidget rejectBtn;

        IncomingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<ResourceLocation> skinGetter) {
            super(screen, data.name(), skinGetter);
            this.data = data;
            acceptBtn = new IconButtonWidget(0, 0, 20, 20,
                    trStr("screen.msf_friends.friends.button.accept_friend_request"),
                    b -> {
                        MsfFriendsBoot c = MsfFriendsBoot.get();
                        if (c != null && c.social() != null) {
                            c.social().acceptIncomingFriendRequest(data.id()).whenComplete((result, err) -> {
                                if (this.minecraft != null) this.minecraft.enqueue(screen::refreshLists);
                            });
                        }
                    }, ICON_ACCEPT, 12, 12);
            rejectBtn = new IconButtonWidget(0, 0, 20, 20,
                    trStr("screen.msf_friends.friends.button.decline_friend_request"),
                    b -> {
                        MsfFriendsBoot c = MsfFriendsBoot.get();
                        if (c != null && c.social() != null) {
                            c.social().declineIncomingFriendRequest(data.id()).whenComplete((result, err) -> {
                                if (this.minecraft != null) this.minecraft.enqueue(screen::refreshLists);
                            });
                        }
                    }, ICON_REJECT, 12, 12);
        }

        @Override
        public void render(int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float delta) {
            boolean hovering = isMouseOver;
            if (hovering) AbstractGui.fill(left, top, left + width, top + height, 0x22FFFFFF);
            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            renderFace(skinGetter.get(), skinX, skinY);
            int textX = skinX + SKIN_SIZE + 4;
            minecraft.field_71466_p.drawString(playerName, textX, top + (height - 9) / 2, PLAYERNAME_COLOR);
            int btnX = left + width - 4;
            btnX -= 22;
            rejectBtn.x = btnX;
            rejectBtn.y = top + (height - 20) / 2;
            rejectBtn.render(mouseX, mouseY, delta);
            btnX -= 22;
            acceptBtn.x = btnX;
            acceptBtn.y = top + (height - 20) / 2;
            acceptBtn.render(mouseX, mouseY, delta);
        }
    }

    // ============ Outgoing request entry ============

    static class OutgoingEntry extends BaseEntry {
        private final IconButtonWidget cancelBtn;

        OutgoingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<ResourceLocation> skinGetter) {
            super(screen, data.name(), skinGetter);
            cancelBtn = new IconButtonWidget(0, 0, 20, 20,
                    trStr("screen.msf_friends.friends.button.revoke_friend_request"),
                    b -> {
                        MsfFriendsBoot c = MsfFriendsBoot.get();
                        if (c != null && c.social() != null) {
                            c.social().revokeOutgoingFriendRequest(data.id()).whenComplete((result, err) -> {
                                if (this.minecraft != null) this.minecraft.enqueue(screen::refreshLists);
                            });
                        }
                    }, ICON_CANCEL, 12, 12);
        }

        @Override
        public void render(int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float delta) {
            boolean hovering = isMouseOver;
            if (hovering) AbstractGui.fill(left, top, left + width, top + height, 0x22FFFFFF);
            int skinX = left + 4;
            int skinY = top + (height - SKIN_SIZE) / 2;
            renderFace(skinGetter.get(), skinX, skinY);
            int textX = skinX + SKIN_SIZE + 4;
            minecraft.field_71466_p.drawString(playerName, textX, top + (height - 9) / 2, PLAYERNAME_COLOR);
            cancelBtn.x = left + width - 24;
            cancelBtn.y = top + (height - 20) / 2;
            cancelBtn.render(mouseX, mouseY, delta);
        }
    }
}
