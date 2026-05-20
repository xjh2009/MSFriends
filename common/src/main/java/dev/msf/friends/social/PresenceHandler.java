package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.PresenceResponse;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Strict 26.2 port of {@code PresenceHandler}.
 *
 * <p>Two pieces are bridged:
 * <ul>
 *   <li>The {@link PresenceStatus} chosen for the broadcast — depends on
 *       {@link MinecraftBridge#presenceSharing()} and {@link MinecraftBridge#multiplayerScope()}.</li>
 *   <li>{@link #tick()} is called by the mod's host (HTTP server pulse or, later,
 *       a client-tick hook), exactly once a second.</li>
 * </ul>
 */
public class PresenceHandler {
    private static final Logger LOGGER = Logging.get();
    private static final Duration PRESENCE_UPDATE_INTERVAL = Duration.ofSeconds(10L);
    private static final Duration MAX_PRESENCE_UPDATE_INTERVAL = Duration.ofSeconds(60L);

    private final Set<UUID> invitedPlayersBatch = ConcurrentHashMap.newKeySet();
    private final Set<UUID> locallyDismissedInvitePmids = ConcurrentHashMap.newKeySet();
    private final Set<UUID> seenInvites = ConcurrentHashMap.newKeySet();
    /** profileId → pmid mapping, built from presence responses. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, UUID> profileIdToPmid = new java.util.concurrent.ConcurrentHashMap<>();
    /** pmid → profileId mapping, built from presence responses and signaling metadata. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, UUID> pmidToProfileId = new java.util.concurrent.ConcurrentHashMap<>();
    /** profileId → last known status, for change detection. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, String> lastKnownStatus = new java.util.concurrent.ConcurrentHashMap<>();
    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final Supplier<PlayerSocialManager> socialSupplier;

    private volatile PresenceResponse latestPresence = new PresenceResponse(new ArrayList<>());
    private final CopyOnWriteArrayList<Runnable> presenceListeners = new CopyOnWriteArrayList<>();
    // Set to EPOCH so the first tick immediately fires (since > MAX_INTERVAL)
    private volatile Instant lastPresencePost = Instant.EPOCH;
    private volatile boolean updatePresence = true;
    private volatile boolean hiddenModeOfflinePushPending;

    public PresenceHandler(MinecraftBridge bridge, FriendsService friendsService,
                           Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.friendsService = friendsService;
        this.socialSupplier = socialSupplier;
    }

    public void tick() {
        if (shouldRefreshPresence()) {
            LOGGER.info("[presence] tick -> updating presence now");
            updatePresence();
        }
    }

    public void tryUpdatePresence() {
        if (bridge.hiddenMode() && !hiddenModeOfflinePushPending) return;
        this.updatePresence = true;
    }

    public void setHiddenMode(boolean hidden) {
        if (bridge.hiddenMode() == hidden && (!hidden || !hiddenModeOfflinePushPending)) {
            return;
        }

        bridge.setHiddenMode(hidden);
        if (hidden) {
            clearInvites();
            hiddenModeOfflinePushPending = true;
        } else {
            hiddenModeOfflinePushPending = false;
        }
        this.lastPresencePost = Instant.EPOCH;
        this.updatePresence = true;
        tick();
    }

    public PresenceResponse getLatestPresence() { return latestPresence; }

    public void addPresenceListener(Runnable r) { presenceListeners.add(r); }
    public void removePresenceListener(Runnable r) { presenceListeners.remove(r); }

    public Set<UUID> getInvitedPlayersBatch() { return invitedPlayersBatch; }

    public void invitePlayer(UUID profileId) {
        if (invitedPlayersBatch.add(profileId)) {
            tryUpdatePresence();
            CompletableFuture.delayedExecutor(1L, TimeUnit.MINUTES).execute(() -> expireHostInvite(profileId));
        }
    }

    public boolean clearInviteForPmid(UUID pmid) {
        UUID profileId = getProfileIdFromPmid(pmid);
        if (profileId != null && invitedPlayersBatch.remove(profileId)) {
            tryUpdatePresence();
            return true;
        }
        return false;
    }

    public void clearInvites() { invitedPlayersBatch.clear(); }

    public void dismissInviteForPmid(UUID pmid) {
        locallyDismissedInvitePmids.add(pmid);
        UUID profileId = getProfileIdFromPmid(pmid);
        if (profileId != null) seenInvites.remove(profileId);
        tryUpdatePresence();
    }

    public boolean hasDismissedInvite(PresenceStatusDto presence) {
        return locallyDismissedInvitePmids.contains(presence.pmid());
    }

    public boolean isInvitedPmid(UUID pmid) {
        UUID profileId = getProfileIdFromPmid(pmid);
        return profileId != null && invitedPlayersBatch.contains(profileId);
    }

    public void rememberPmidMapping(UUID profileId, UUID pmid) {
        UUID previousPmid = profileIdToPmid.put(profileId, pmid);
        if (previousPmid != null && !previousPmid.equals(pmid)) {
            pmidToProfileId.remove(previousPmid, profileId);
        }
        pmidToProfileId.put(pmid, profileId);
    }

    @Nullable
    public UUID getProfileIdFromPmid(UUID pmid) {
        UUID mapped = pmidToProfileId.get(pmid);
        if (mapped != null) return mapped;

        for (PresenceStatusDto p : latestPresence.presence()) {
            if (pmid.equals(p.pmid())) {
                rememberPmidMapping(p.profileId(), p.pmid());
                return p.profileId();
            }
        }
        return null;
    }

    private boolean shouldRefreshPresence() {
        PlayerSocialManager social = socialSupplier.get();
        if (!social.isFriendListEnabled() || social.getFriends().isEmpty()) return false;
        if (bridge.hiddenMode() && !hiddenModeOfflinePushPending) return false;
        Duration since = Duration.between(lastPresencePost, Instant.now());
        return (updatePresence && since.compareTo(PRESENCE_UPDATE_INTERVAL) >= 0)
                || since.compareTo(MAX_PRESENCE_UPDATE_INTERVAL) >= 0;
    }

    private void updatePresence() {
        updatePresence = false;
        lastPresencePost = Instant.now();
        PresenceStatus status = getPresenceStatus();
        JoinInfoUpdate joinInfo = getJoinInfoUpdate(status);
        LOGGER.info("[presence] pushing status={} joinInfo={}", status.name(), joinInfo);
        CompletableFuture.runAsync(() -> {
            LOGGER.info("[presence] calling friendsService.presence() on thread {}", Thread.currentThread().getName());
            PresenceResponse response;
            try { response = friendsService.presence(status.name(), joinInfo); }
            catch (Throwable t) { LOGGER.error("[presence] presence() threw", t); return; }
            LOGGER.info("[presence] got response with {} entries (object={})", response.presence().size(), response);
            // --- Debug: dump every presence entry so we can see invite flags without UI ---
            for (PresenceStatusDto p : response.presence()) {
                PresenceStatusDto.JoinInfo ji = p.joinInfo();
                LOGGER.info("[presence][entry] profileId={} pmid={} status={} invited={} joinValue={}",
                        p.profileId(), p.pmid(), p.status(),
                        ji != null && ji.invited(),
                        ji != null ? ji.value() : null);
            }
            bridge.executeOnClientThread(() -> {
                this.latestPresence = response;
                if (bridge.hiddenMode()) {
                    hiddenModeOfflinePushPending = false;
                }
                // Update profileId → pmid mapping from every presence entry
                for (PresenceStatusDto p : response.presence()) {
                    rememberPmidMapping(p.profileId(), p.pmid());
                }
                // Detect status changes and notify (excluding transitions TO offline)
                detectStatusChanges(response);
                clearStaleDismissedInvites();
                // Detect new invites and expired invites
                Set<UUID> currentlyInvitedBy = new java.util.HashSet<>();
                for (PresenceStatusDto presence : response.presence()) {
                    PresenceStatusDto.JoinInfo ji = presence.joinInfo();
                    if (ji != null && ji.invited()) {
                        currentlyInvitedBy.add(presence.profileId());
                        socialSupplier.get().getFriends().stream()
                                .filter(p -> p.id().equals(presence.profileId())
                                        && !seenInvites.contains(p.id()))
                                .findAny()
                                .ifPresent(p -> {
                                    seenInvites.add(p.id());
                                    if (NotificationPrefs.get().notifyInvite)
                                        bridge.notifyToast("friend.invite_from", p.name(), p.id());
                                });
                    }
                }
                // Detect expired invites: was in seenInvites but no longer invited
                Set<UUID> expiredInvites = new java.util.HashSet<>(seenInvites);
                expiredInvites.removeAll(currentlyInvitedBy);
                for (UUID expiredId : expiredInvites) {
                    seenInvites.remove(expiredId);
                    socialSupplier.get().getFriends().stream()
                            .filter(p -> p.id().equals(expiredId))
                            .findAny()
                            .ifPresent(p -> { if (NotificationPrefs.get().notifyInvite) bridge.notifyToast("friend.invite_expired", p.name(), p.id()); });
                }
                // Notify UI listeners that presence has been updated
                presenceListeners.forEach(Runnable::run);
            });
        });
    }

    private void detectStatusChanges(PresenceResponse response) {
        for (PresenceStatusDto dto : response.presence()) {
            UUID profileId = dto.profileId();
            String newStatus = dto.status().name();
            String oldStatus = lastKnownStatus.put(profileId, newStatus);

            // Skip if status hasn't changed, or if transitioning TO offline
            if (newStatus.equals(oldStatus)) continue;
            if ("OFFLINE".equals(newStatus)) continue;

            // Skip the first time we see someone (no previous state)
            if (oldStatus == null) continue;

            // Find friend name
            String friendName = socialSupplier.get().getFriends().stream()
                    .filter(p -> p.id().equals(profileId))
                    .map(PlayerSocialManager.PlayerData::name)
                    .findFirst().orElse(profileId.toString().substring(0, 8));

            String toastType = switch (newStatus) {
                case "ONLINE"                -> "friend.status.online";
                case "PLAYING_OFFLINE"       -> "friend.status.playing_offline";
                case "PLAYING_HOSTED_SERVER" -> "friend.status.hosting";
                case "PLAYING_REALMS"        -> "friend.status.realms";
                case "PLAYING_SERVER"        -> "friend.status.server";
                default -> null;
            };

            if (toastType != null) {
                NotificationPrefs prefs = NotificationPrefs.get();
                boolean allowed = "ONLINE".equals(newStatus) ? prefs.notifyOnline : prefs.notifyStatus;
                if (allowed) bridge.notifyToast(toastType, friendName, profileId);
            }
        }
    }

    private void clearStaleDismissedInvites() {
        locallyDismissedInvitePmids.removeIf(pmid ->
                latestPresence.presence().stream().noneMatch(p ->
                        pmid.equals(p.pmid()) && p.joinInfo() != null && p.joinInfo().invited()));
    }

    private PresenceStatus getPresenceStatus() {
        if (bridge.hiddenMode()) return PresenceStatus.OFFLINE;
        return switch (bridge.presenceSharing()) {
            case NONE -> PresenceStatus.OFFLINE;
            case LIMITED -> PresenceStatus.ONLINE;
            case ALL -> {
                if (bridge.isHostingP2P()) {
                    // hosting online -> PLAYING_HOSTED_SERVER
                    yield switch (bridge.multiplayerScope()) {
                        case OFF, LAN -> PresenceStatus.PLAYING_OFFLINE;
                        case ONLINE   -> PresenceStatus.PLAYING_HOSTED_SERVER;
                    };
                }
                if (bridge.inLevel()) yield PresenceStatus.PLAYING_OFFLINE;
                yield PresenceStatus.ONLINE;
            }
        };
    }

    @Nullable
    private JoinInfoUpdate getJoinInfoUpdate(@Nullable PresenceStatus status) {
        if (status == null) return null;
        if (bridge.hiddenMode()) return null;
        // value must be absent for PLAYING_HOSTED_SERVER (server returns 400 otherwise).
        // invites contains profile IDs (NOT pmids) — matches 26.2 bytecode: invitedPlayersBatch
        // is passed directly without pmid conversion.
        return switch (bridge.presenceSharing()) {
            case NONE -> null;
            case LIMITED -> {
                Set<UUID> ids = Set.copyOf(invitedPlayersBatch);
                yield ids.isEmpty() ? null : new JoinInfoUpdate(null, ids);
            }
            case ALL -> switch (status) {
                case PLAYING_HOSTED_SERVER -> new JoinInfoUpdate(null, Set.copyOf(invitedPlayersBatch));
                case ONLINE, PLAYING_OFFLINE, OFFLINE, PLAYING_REALMS, PLAYING_SERVER -> null;
            };
        };
    }

    /** Convert invitedPlayersBatch (profileIds) to PMIDs using the mapping table. */
    private Set<UUID> resolveInvitedPmids() {
        if (invitedPlayersBatch.isEmpty()) return Set.of();
        Set<UUID> pmids = new java.util.HashSet<>();
        for (UUID profileId : invitedPlayersBatch) {
            UUID pmid = profileIdToPmid.get(profileId);
            if (pmid != null) {
                pmids.add(pmid);
            } else {
                LOGGER.debug("[presence] cannot resolve pmid for profileId={} (not seen in presence yet)", profileId);
            }
        }
        return Set.copyOf(pmids);
    }

    /** Get the pmid for a given profileId (if known from presence). */
    @Nullable
    public UUID getPmidFromProfileId(UUID profileId) {
        return profileIdToPmid.get(profileId);
    }

    private void expireHostInvite(UUID profileId) {
        if (!invitedPlayersBatch.remove(profileId)) return;
        tryUpdatePresence();
        socialSupplier.get().getFriends().stream()
                .filter(p -> p.id().equals(profileId))
                .findAny()
                .ifPresent(p -> { if (NotificationPrefs.get().notifyInvite) bridge.notifyToast("friend.host_invite_expired", p.name(), p.id()); });
    }
}
