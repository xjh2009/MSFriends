package dev.msf.friends.screen;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.MsfFriendsBoot;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.p2p.FriendJoinHandler;
import dev.msf.friends.social.PlayerSocialManager;
import dev.msf.friends.social.PresenceHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Full friends screen modeled after 26.1.2, adapted to 1.16.5 Yarn API.
 *
 * Key 1.16.5 differences from 26.1.2:
 * - MatrixStack instead of GuiGraphicsExtractor
 * - ButtonWidget instead of Button.builder()
 * - AlwaysSelectedEntryListWidget instead of ContainerObjectSelectionList
 * - TextFieldWidget instead of EditBox
 * - drawTexture/drawCenteredText/drawStringWithShadow instead of graphics methods
 * - Text/TranslatableText instead of Component
 * - I18n.translate() instead of I18n.get()
 * - No PlayerSkin/PlayerFaceExtractor; uses Identifier + drawTexture for skin rendering
 * - addButton() instead of addRenderableWidget()
 * - No SpriteIconButton; uses plain ButtonWidget for action buttons
 */
public class FriendsScreen extends Screen {
    private static final Text TITLE = new TranslatableText("screen.msf_friends.friends.title");
    private static final Text SEARCH_HINT = new TranslatableText("screen.msf_friends.friends.search_hint");
    private static final Text TAB_FRIENDS = new TranslatableText("screen.msf_friends.friends.tab.friends");
    private static final Text TAB_PENDING = new TranslatableText("screen.msf_friends.friends.tab.pending");
    private static final Text BUTTON_ADD = new TranslatableText("screen.msf_friends.friends.button.add");
    private static final Text EMPTY_FRIENDS = new TranslatableText("screen.msf_friends.friends.empty.friends");
    private static final Text EMPTY_PENDING = new TranslatableText("screen.msf_friends.friends.empty.pending");
    private static final Text EMPTY_FILTER = new TranslatableText("screen.msf_friends.friends.empty.filter");
    private static final int BG_WIDTH = 236;
    private static final int ITEM_HEIGHT = 36;
    static final int SKIN_SIZE = 24;

    private static final Identifier BACKGROUND_TEXTURE = new Identifier("minecraft", "textures/gui/social_interactions.png");
    // 1.16.5 does not have the search icon texture; we draw a "?" text symbol instead

    private final Screen parent;
    private Page page = Page.FRIENDS;
    private @Nullable FriendsPlayerList playerList;
    private @Nullable TextFieldWidget searchBox;
    private @Nullable ButtonWidget friendsButton;
    private @Nullable ButtonWidget pendingButton;
    private @Nullable ButtonWidget addFriendButton;
    private @Nullable Runnable updateListener;
    private @Nullable Runnable presenceListener;
    private @Nullable Text statusMessage;
    private int statusColor = 0xFFFFFF;
    private final Map<UUID, Identifier> resolvedSkins = new ConcurrentHashMap<>();
    private final Set<UUID> requestedSkins = ConcurrentHashMap.newKeySet();

    public FriendsScreen(@Nullable Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    private int windowHeight() {
        return Math.max(96, panelBottom() - 64);
    }

    private int listTop() { return 96; }
    private int doneY() { return this.height - 30; }
    private int panelBottom() { return this.doneY() - 8; }
    private int listBottom() { return this.panelBottom() - 8; }
    private int marginX() { return (this.width - 238) / 2; }

    @Override
    protected void init() {
        super.init();
        // Player list
        this.playerList = new FriendsPlayerList(this.client, this.width, this.listBottom() - this.listTop(), this.listTop(), this.listBottom(), ITEM_HEIGHT, this);

        // Tabs
        int tabLeft = this.marginX() + 3;
        int tabW = BG_WIDTH / 2;
        this.friendsButton = this.addButton(new ButtonWidget(tabLeft, 45, tabW, 20, TAB_FRIENDS, b -> showPage(Page.FRIENDS)));
        this.pendingButton = this.addButton(new ButtonWidget(tabLeft + tabW, 45, tabW, 20, TAB_PENDING, b -> showPage(Page.PENDING)));

        // Search box
        String oldSearch = this.searchBox != null ? this.searchBox.getText() : "";
        this.searchBox = this.addButton(new TextFieldWidget(this.textRenderer, this.marginX() + 28, 72, 152, 20, SEARCH_HINT));
        this.searchBox.setMaxLength(36);
        this.searchBox.setVisible(true);
        this.searchBox.setEditableColor(-1);
        this.searchBox.setText(oldSearch);
        this.searchBox.setSuggestion(SEARCH_HINT.getString());
        this.searchBox.setChangedListener(value -> refreshLists());

        // Add button
        this.addFriendButton = this.addButton(new ButtonWidget(this.marginX() + 184, 72, 20, 20, BUTTON_ADD, b -> submitFriendRequestFromSearch()));

        // Player list widget
        this.children.add(this.playerList);

        // Done button
        this.addButton(new ButtonWidget(this.width / 2 - 100, this.doneY(), 200, 20, new TranslatableText("gui.done"), b -> this.onClose()));

        // Listeners
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
            if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
                submitFriendRequestFromSearch();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void showPage(Page page) {
        this.page = page;
        refreshLists();
    }

    private void setStatus(Text message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
    }

    private void submitFriendRequestFromSearch() {
        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null || this.searchBox == null || this.client == null) {
            setStatus(new TranslatableText("message.msf_friends.service_not_ready"), 0xFFFF8080);
            return;
        }

        String value = this.searchBox.getText().trim();
        if (value.isEmpty()) {
            setStatus(new TranslatableText("message.msf_friends.enter_player_or_uuid"), 0xFFFFAA00);
            return;
        }

        CompletableFuture<FriendsService.ResultCode> action;
        try {
            action = looksLikeUuid(value)
                    ? client.social().sendFriendRequest(UUID.fromString(value))
                    : client.social().sendFriendRequest(value);
        } catch (IllegalArgumentException ex) {
            setStatus(new TranslatableText("message.msf_friends.invalid_uuid"), 0xFFFF8080);
            return;
        }

        setStatus(new TranslatableText("message.msf_friends.sending_friend_request"), 0xFFE0E0E0);
        action.whenComplete((result, error) -> this.client.execute(() -> {
            if (error != null) {
                setStatus(new TranslatableText("message.msf_friends.friend_request_send_failed", throwableMessage(error)), 0xFFFF8080);
            } else {
                applyResultMessage(new TranslatableText("message.msf_friends.friend_request_sent"), result);
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
            case TOO_MANY_REQUESTS -> setStatus(new TranslatableText("message.msf_friends.result.too_many_requests"), 0xFFFFAA00);
            case UNKNOWN_PROFILE -> setStatus(new TranslatableText("message.msf_friends.result.unknown_profile"), 0xFFFF8080);
            case FORBIDDEN -> setStatus(new TranslatableText("message.msf_friends.result.forbidden"), 0xFFFF8080);
            case SERVICE_NOT_AVAILABLE, TEMPORARY_UNAVAILABLE -> setStatus(new TranslatableText("message.msf_friends.result.service_not_available"), 0xFFFF8080);
            case CONNECTION_ISSUE -> setStatus(new TranslatableText("message.msf_friends.result.connection_issue"), 0xFFFF8080);
            case UPGRADE_NEEDED -> setStatus(new TranslatableText("message.msf_friends.result.upgrade_needed"), 0xFFFF8080);
            case GENERIC_ERROR, ERROR -> setStatus(new TranslatableText("message.msf_friends.result.generic_error"), 0xFFFF8080);
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

    /** Get or start loading a skin for a profile. Returns a Supplier that yields the resolved skin Identifier or null. */
    private Supplier<Identifier> getSkinGetter(UUID profileId, String name) {
        return () -> {
            Identifier skin = this.resolvedSkins.get(profileId);
            if (skin != null) return skin;

            MinecraftClient mc = this.client;
            if (mc != null && this.requestedSkins.add(profileId)) {
                PlayerSkinResolver.fetchSkin(mc, profileId, name)
                        .thenAccept(resolved -> this.resolvedSkins.put(profileId, resolved));
            }
            return null; // not yet resolved
        };
    }

    void refreshLists() {
        if (playerList == null) return;
        List<BaseEntry> entries = new ArrayList<>();

        var client = MsfFriendsBoot.get();
        if (client == null || client.social() == null) {
            playerList.replaceEntriesPublic(entries);
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

        playerList.replaceEntriesPublic(entries);
    }

    @Override
    public void onClose() {
        if (this.client != null) {
            this.client.openScreen(this.parent);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        // Background panel
        int mx = this.marginX() + 3;
        MinecraftClient.getInstance().getTextureManager().bindTexture(BACKGROUND_TEXTURE);
        drawTexture(matrices, mx, 64, 0f, 0f, BG_WIDTH, this.windowHeight(), 256, 256);
        // Search icon — 1.16.5 doesn't have the search.png icon; draw a magnifying glass symbol instead
        drawStringWithShadow(matrices, this.textRenderer, "\u2315", mx + 10, 76, 0xFFAAAAAA);

        // Title
        drawCenteredText(matrices, this.textRenderer, TITLE, this.width / 2, 8, -1);
        if (this.statusMessage != null) {
            drawCenteredText(matrices, this.textRenderer, this.statusMessage, this.width / 2, 24, this.statusColor);
        }

        // Dim unselected tab
        ButtonWidget unselTab = (page == Page.FRIENDS) ? pendingButton : friendsButton;
        if (unselTab != null) {
            fill(matrices, unselTab.x, unselTab.y, unselTab.x + unselTab.getWidth(), unselTab.y + 20, 0x99000000);
        }

        // Player list or empty message
        if (playerList != null && !playerList.children().isEmpty()) {
            playerList.render(matrices, mouseX, mouseY, delta);
        } else {
            Text empty = hasFilter() ? EMPTY_FILTER : (page == Page.FRIENDS ? EMPTY_FRIENDS : EMPTY_PENDING);
            drawCenteredText(matrices, this.textRenderer, empty, this.width / 2, (this.listTop() + this.listBottom()) / 2, 0xAAAAAA);
        }

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    enum Page { FRIENDS, PENDING }

    // ============ Player List ============

    @SuppressWarnings("rawtypes")
    static class FriendsPlayerList extends AlwaysSelectedEntryListWidget {
        private final FriendsScreen screen;

        public FriendsPlayerList(MinecraftClient mc, int width, int height, int top, int bottom, int itemHeight, FriendsScreen screen) {
            super(mc, width, height, top, bottom, itemHeight);
            this.screen = screen;
            this.left = screen.marginX() + 3;
            this.right = this.left + BG_WIDTH;
        }

        public int getListTop() { return this.top; }

        @Override
        public int getRowWidth() {
            return 200;
        }

        @Override
        public int getRowLeft() {
            return this.left + (BG_WIDTH - getRowWidth()) / 2;
        }

        @Override
        protected int getScrollbarPositionX() {
            return this.left + (this.right - this.left + getRowWidth()) / 2 + 4;
        }

        @SuppressWarnings("unchecked")
        public void replaceEntriesPublic(java.util.Collection entries) {
            this.replaceEntries(entries);
        }
    }

    // ============ Entry base ============

    static abstract class BaseEntry extends AlwaysSelectedEntryListWidget.Entry<BaseEntry> {
        protected final FriendsScreen screen;
        protected final String playerName;
        protected final Supplier<Identifier> skinGetter;

        BaseEntry(FriendsScreen screen, String name, Supplier<Identifier> skinGetter) {
            this.screen = screen;
            this.playerName = name;
            this.skinGetter = skinGetter;
        }

        /**
         * Render the base: skin face + name text.
         * Layout matches 26.1.2: skin at x+4, name at x+4+SKIN_SIZE+4
         * Returns textStartX for subclass use.
         */
        protected int renderBase(MatrixStack matrices, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered) {
            // Hover highlight
            if (hovered) {
                fill(matrices, x, y, x + rowWidth, y + rowHeight, 0x22FFFFFF);
            }

            // Skin face — 1.16.5: use Identifier + drawTexture (no PlayerFaceExtractor)
            int skinX = x + 4;
            int skinY = y + (rowHeight - SKIN_SIZE) / 2;
            Identifier skinId = skinGetter.get();
            if (skinId != null) {
                // Bind the skin texture and draw the face portion (8x8 area at 8,8 scaled to SKIN_SIZE)
                RenderSystem.enableBlend();
                MinecraftClient.getInstance().getTextureManager().bindTexture(skinId);
                // Face is the 8x8 area starting at (8,8) in the 64x64 skin texture, scaled 8x to 64x64
                // We draw it at SKIN_SIZE x SKIN_SIZE
                int texScale = 8; // skin texture is 8x per pixel
                drawTexture(matrices, skinX, skinY, SKIN_SIZE, SKIN_SIZE,
                        8 * texScale, 8 * texScale, 8 * texScale, 8 * texScale,
                        64 * texScale, 64 * texScale);
                // Draw the hat layer on top (same region, at 40,8)
                drawTexture(matrices, skinX, skinY, SKIN_SIZE, SKIN_SIZE,
                        40 * texScale, 8 * texScale, 8 * texScale, 8 * texScale,
                        64 * texScale, 64 * texScale);
                RenderSystem.disableBlend();
            } else {
                // Placeholder while loading
                fill(matrices, skinX, skinY, skinX + SKIN_SIZE, skinY + SKIN_SIZE, 0xFF555555);
            }

            // Name
            int textX = skinX + SKIN_SIZE + 4;
            int nameY = y + rowHeight / 3 - 4;
            drawStringWithShadow(matrices, MinecraftClient.getInstance().textRenderer, playerName, textX, nameY, 0xFFFFFF);

            return textX;
        }
    }

    // ============ Friend entry ============

    static class FriendEntry extends BaseEntry {
        private final @Nullable Text status;
        private final int statusColor;
        private final PlayerSocialManager.PlayerData data;
        private final boolean hosting;
        private final boolean canJoin;
        private final Map<UUID, String> joinRequests;
        private final @Nullable UUID friendPmid;
        private final PresenceHandler presenceHandler;

        FriendEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data,
                    Supplier<Identifier> skinGetter, PresenceHandler presenceHandler,
                    boolean hosting, boolean canJoin, Map<UUID, String> incomingJoinRequests) {
            super(screen, data.name(), skinGetter);
            this.data = data;
            this.hosting = hosting;
            this.canJoin = canJoin;
            this.joinRequests = incomingJoinRequests;
            this.presenceHandler = presenceHandler;

            PresenceStatusDto pres = presenceHandler.getLatestPresence().presence().stream()
                    .filter(p -> p.profileId().equals(data.id())).findFirst().orElse(null);

            this.status = statusText(pres);
            this.statusColor = statusColor(pres);
            this.friendPmid = pres != null ? pres.pmid() : null;
        }

        @Override
        public void render(MatrixStack matrices, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            int textX = renderBase(matrices, index, y, x, rowWidth, rowHeight, mouseX, mouseY, hovered);

            // Status text below name
            int nameY = y + rowHeight / 3 - 4;
            if (status != null) {
                drawStringWithShadow(matrices, MinecraftClient.getInstance().textRenderer, status.getString(), textX, nameY + 12, statusColor);
            }

            // Action buttons on the right side
            int btnX = x + rowWidth - 4;
            int btnY = y + (rowHeight - 20) / 2;
            boolean hasPendingJoinReq = hosting && friendPmid != null && joinRequests.containsKey(friendPmid);

            PresenceStatusDto pres = presenceHandler.getLatestPresence().presence().stream()
                    .filter(p -> p.profileId().equals(data.id())).findFirst().orElse(null);
            boolean isInvited = pres != null && pres.joinInfo() != null && pres.joinInfo().invited()
                    && !presenceHandler.hasDismissedInvite(pres);

            if (hasPendingJoinReq) {
                // Accept join request button
                btnX -= 22;
                drawActionButton(matrices, "\u2714", btnX, btnY, 0x55FF55, mouseX, mouseY);
                // Reject join request button
                btnX -= 22;
                drawActionButton(matrices, "\u2716", btnX, btnY, 0xFF5555, mouseX, mouseY);
            } else if (pres != null && "PLAYING_HOSTED_SERVER".equals(pres.status().name())
                    && friendPmid != null && canJoin && isInvited) {
                // Accept invite button
                btnX -= 22;
                drawActionButton(matrices, "\u2714", btnX, btnY, 0x55FF55, mouseX, mouseY);
                // Reject invite button
                btnX -= 22;
                drawActionButton(matrices, "\u2716", btnX, btnY, 0xFF5555, mouseX, mouseY);
            } else if (pres != null && "PLAYING_HOSTED_SERVER".equals(pres.status().name())
                    && friendPmid != null && canJoin) {
                // Request join button
                btnX -= 22;
                drawActionButton(matrices, "\u27A4", btnX, btnY, 0x55FF55, mouseX, mouseY);
            } else if (hosting && pres != null && "ONLINE".equals(pres.status().name())
                    && !presenceHandler.getInvitedPlayersBatch().contains(data.id())) {
                // Invite button
                btnX -= 22;
                drawActionButton(matrices, "\u2795", btnX, btnY, 0x55FF55, mouseX, mouseY);
            }

            // Remove button (always visible, rightmost)
            btnX -= 22;
            drawActionButton(matrices, "\u00D7", btnX, btnY, 0xFF5555, mouseX, mouseY);
        }

        private void drawActionButton(MatrixStack matrices, String label, int x, int y, int color, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            if (hovered) {
                fill(matrices, x, y, x + 20, y + 20, 0x33FFFFFF);
            }
            drawStringWithShadow(matrices, MinecraftClient.getInstance().textRenderer, label, x + 6, y + 6, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            var client = MsfFriendsBoot.get();
            if (client == null) return super.mouseClicked(mouseX, mouseY, button);

            // Determine which action area was clicked
            // We need to know our row position — use the list's internal tracking
            int rowWidth = 200;
            int rowHeight = ITEM_HEIGHT;

            // Get our row index from the parent list
            int idx = screen.playerList.children().indexOf(this);
            if (idx < 0) return super.mouseClicked(mouseX, mouseY, button);

            int x = screen.playerList.getRowLeft();
            int y = idx * ITEM_HEIGHT + screen.playerList.getListTop();
            int btnX = x + rowWidth - 4;
            int btnY = y + (rowHeight - 20) / 2;

            boolean hasPendingJoinReq = hosting && friendPmid != null && joinRequests.containsKey(friendPmid);
            PresenceStatusDto pres = presenceHandler.getLatestPresence().presence().stream()
                    .filter(p -> p.profileId().equals(data.id())).findFirst().orElse(null);
            boolean isInvited = pres != null && pres.joinInfo() != null && pres.joinInfo().invited()
                    && !presenceHandler.hasDismissedInvite(pres);

            // Calculate button positions in same order as render
            List<ButtonArea> buttons = new ArrayList<>();

            if (hasPendingJoinReq) {
                btnX -= 22;
                buttons.add(new ButtonArea(btnX, btnY, "accept_join"));
                btnX -= 22;
                buttons.add(new ButtonArea(btnX, btnY, "reject_join"));
            } else if (pres != null && "PLAYING_HOSTED_SERVER".equals(pres.status().name())
                    && friendPmid != null && canJoin && isInvited) {
                btnX -= 22;
                buttons.add(new ButtonArea(btnX, btnY, "accept_invite"));
                btnX -= 22;
                buttons.add(new ButtonArea(btnX, btnY, "reject_invite"));
            } else if (pres != null && "PLAYING_HOSTED_SERVER".equals(pres.status().name())
                    && friendPmid != null && canJoin) {
                btnX -= 22;
                buttons.add(new ButtonArea(btnX, btnY, "request_join"));
            } else if (hosting && pres != null && "ONLINE".equals(pres.status().name())
                    && !presenceHandler.getInvitedPlayersBatch().contains(data.id())) {
                btnX -= 22;
                buttons.add(new ButtonArea(btnX, btnY, "invite"));
            }
            btnX -= 22;
            buttons.add(new ButtonArea(btnX, btnY, "remove"));

            // Check which button was clicked
            for (var b : buttons) {
                if (mouseX >= b.x && mouseX < b.x + 20 && mouseY >= b.y && mouseY < b.y + 20) {
                    handleAction(b.action, client);
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        private void handleAction(String action, MsfFriendsBoot client) {
            switch (action) {
                case "remove" -> {
                    String friendName = data.name();
                    MinecraftClient.getInstance().openScreen(new ConfirmScreen(
                        confirmed -> {
                            MinecraftClient.getInstance().openScreen(screen);
                            if (confirmed) {
                                client.social().removeFriend(data.id()).whenComplete((result, err) -> {
                                    if (result == FriendsService.ResultCode.SUCCESS) {
                                        MinecraftClient.getInstance().execute(() -> FriendToast.show(
                                            new TranslatableText("screen.msf_friends.friends.toast.friend_removed.title"),
                                            new TranslatableText("screen.msf_friends.friends.toast.friend_removed.description", friendName),
                                            data.id()
                                        ));
                                    }
                                });
                            }
                        },
                        new TranslatableText("screen.msf_friends.friends.confirm_remove.title"),
                        new TranslatableText("screen.msf_friends.friends.confirm_remove.message", data.name())
                    ));
                }
                case "accept_join" -> {
                    if (friendPmid != null && client.p2p() != null) {
                        client.p2p().acceptIncomingJoinRequest(friendPmid);
                        screen.refreshLists();
                    }
                }
                case "reject_join" -> {
                    if (friendPmid != null && client.p2p() != null) {
                        client.p2p().rejectIncomingJoinRequest(friendPmid);
                        screen.refreshLists();
                    }
                }
                case "accept_invite" -> {
                    if (friendPmid != null && client.p2p() != null) {
                        client.p2p().joinPlayer(friendPmid.toString())
                                .thenRun(() -> MinecraftClient.getInstance().execute(() ->
                                    P2PConnectScreen.startConnecting(screen, MinecraftClient.getInstance(), friendPmid.toString())))
                                .exceptionally(err -> { screen.refreshLists(); return null; });
                    }
                }
                case "reject_invite" -> {
                    if (friendPmid != null) {
                        if (client.p2p() != null) client.p2p().declineInvite(friendPmid);
                        if (client.social() != null) client.social().getPresenceHandler().dismissInviteForPmid(friendPmid);
                        screen.refreshLists();
                    }
                }
                case "request_join" -> {
                    if (friendPmid != null && client.p2p() != null) {
                        client.p2p().joinPlayer(friendPmid.toString())
                                .thenRun(() -> MinecraftClient.getInstance().execute(() ->
                                    P2PConnectScreen.startConnecting(screen, MinecraftClient.getInstance(), friendPmid.toString())))
                                .exceptionally(err -> { screen.refreshLists(); return null; });
                    }
                }
                case "invite" -> {
                    presenceHandler.invitePlayer(data.id());
                    screen.refreshLists();
                }
            }
        }

        record ButtonArea(int x, int y, String action) {}
    }

    // ============ Incoming friend request entry ============

    static class IncomingEntry extends BaseEntry {
        private final PlayerSocialManager.PlayerData data;

        IncomingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<Identifier> skinGetter) {
            super(screen, data.name(), skinGetter);
            this.data = data;
        }

        @Override
        public void render(MatrixStack matrices, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            int textX = renderBase(matrices, index, y, x, rowWidth, rowHeight, mouseX, mouseY, hovered);

            // Accept / Decline buttons on the right
            int btnX = x + rowWidth - 4;
            int btnY = y + (rowHeight - 20) / 2;
            // Decline
            btnX -= 22;
            drawActionButton(matrices, "\u2716", btnX, btnY, 0xFF5555, mouseX, mouseY);
            // Accept
            btnX -= 22;
            drawActionButton(matrices, "\u2714", btnX, btnY, 0x55FF55, mouseX, mouseY);
        }

        private void drawActionButton(MatrixStack matrices, String label, int x, int y, int color, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            if (hovered) {
                fill(matrices, x, y, x + 20, y + 20, 0x33FFFFFF);
            }
            drawStringWithShadow(matrices, MinecraftClient.getInstance().textRenderer, label, x + 6, y + 6, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            var client = MsfFriendsBoot.get();
            if (client == null || client.social() == null) return super.mouseClicked(mouseX, mouseY, button);

            int idx = screen.playerList.children().indexOf(this);
            if (idx < 0) return super.mouseClicked(mouseX, mouseY, button);

            int x = screen.playerList.getRowLeft();
            int y = idx * ITEM_HEIGHT + screen.playerList.getListTop();
            int btnY = y + (ITEM_HEIGHT - 20) / 2;

            // Decline button
            int declX = x + 200 - 4 - 22;
            // Accept button
            int accX = declX - 22;

            if (mouseX >= declX && mouseX < declX + 20 && mouseY >= btnY && mouseY < btnY + 20) {
                client.social().declineIncomingFriendRequest(data.id());
                screen.refreshLists();
                return true;
            }
            if (mouseX >= accX && mouseX < accX + 20 && mouseY >= btnY && mouseY < btnY + 20) {
                client.social().acceptIncomingFriendRequest(data.id());
                screen.refreshLists();
                return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    // ============ Outgoing friend request entry ============

    static class OutgoingEntry extends BaseEntry {
        private final PlayerSocialManager.PlayerData data;

        OutgoingEntry(FriendsScreen screen, PlayerSocialManager.PlayerData data, Supplier<Identifier> skinGetter) {
            super(screen, data.name(), skinGetter);
            this.data = data;
        }

        @Override
        public void render(MatrixStack matrices, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            int textX = renderBase(matrices, index, y, x, rowWidth, rowHeight, mouseX, mouseY, hovered);
            // Revoke button on the right
            int btnX = x + rowWidth - 24;
            int btnY = y + (rowHeight - 20) / 2;
            drawActionButton(matrices, "\u2716", btnX, btnY, 0xFF5555, mouseX, mouseY);
        }

        private void drawActionButton(MatrixStack matrices, String label, int x, int y, int color, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            if (hovered) {
                fill(matrices, x, y, x + 20, y + 20, 0x33FFFFFF);
            }
            drawStringWithShadow(matrices, MinecraftClient.getInstance().textRenderer, label, x + 6, y + 6, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            var client = MsfFriendsBoot.get();
            if (client == null || client.social() == null) return super.mouseClicked(mouseX, mouseY, button);

            int idx = screen.playerList.children().indexOf(this);
            if (idx < 0) return super.mouseClicked(mouseX, mouseY, button);

            int x = screen.playerList.getRowLeft();
            int y = idx * ITEM_HEIGHT + screen.playerList.getListTop();
            int btnX = x + 200 - 24;
            int btnY = y + (ITEM_HEIGHT - 20) / 2;

            if (mouseX >= btnX && mouseX < btnX + 20 && mouseY >= btnY && mouseY < btnY + 20) {
                client.social().revokeOutgoingFriendRequest(data.id());
                screen.refreshLists();
                return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    // ============ Presence helpers ============

    private static @Nullable Text statusText(@Nullable PresenceStatusDto pres) {
        if (pres == null) return new TranslatableText("screen.msf_friends.friends.presence.offline");
        return switch (pres.status().name()) {
            case "ONLINE" -> new TranslatableText("screen.msf_friends.friends.presence.online");
            case "PLAYING_OFFLINE" -> new TranslatableText("screen.msf_friends.friends.presence.playing_offline");
            case "PLAYING_HOSTED_SERVER" -> new TranslatableText("screen.msf_friends.friends.presence.hosting");
            case "PLAYING_REALMS" -> new TranslatableText("screen.msf_friends.friends.presence.realms");
            case "PLAYING_SERVER" -> new TranslatableText("screen.msf_friends.friends.presence.server");
            default -> new TranslatableText("screen.msf_friends.friends.presence.online");
        };
    }

    /** Status color — matches 26.1.2's color scheme exactly:
     * ONLINE=0xFF55FF55 (green), HOSTING=0xFFFFAA00 (orange),
     * PLAYING_OFFLINE=0xFFAAAAAA (grey), REALMS=0xFFFF55FF (purple),
     * SERVER=0xFF55AAFF (blue), offline=0xFFAAAAAA (grey)
     */
    private static int statusColor(@Nullable PresenceStatusDto pres) {
        if (pres == null) return 0xFFAAAAAA;
        return switch (pres.status().name()) {
            case "ONLINE" -> 0xFF55FF55;
            case "PLAYING_OFFLINE" -> 0xFFAAAAAA;
            case "PLAYING_HOSTED_SERVER" -> 0xFFFFAA00;
            case "PLAYING_REALMS" -> 0xFFFF55FF;
            case "PLAYING_SERVER" -> 0xFF55AAFF;
            default -> 0xFFAAAAAA;
        };
    }
}
