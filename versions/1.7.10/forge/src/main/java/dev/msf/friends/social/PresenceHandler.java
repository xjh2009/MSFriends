package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.request.JoinInfoUpdate;
import com.mojang.authlib.yggdrasil.response.PresenceResponse;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Presence broadcast system (1.7.10 / Java 8 port).
 */
public class PresenceHandler {
    private static final Logger LOGGER = Logging.get(PresenceHandler.class);
    private static final long PRESENCE_UPDATE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10L);
    private static final long MAX_PRESENCE_UPDATE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60L);

    private final Set<UUID> invitedPlayersBatch = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> locallyDismissedInvitePmids = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> seenInvites = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final ConcurrentHashMap<UUID, String> lastKnownStatus = new ConcurrentHashMap<UUID, String>();
    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final Supplier<PlayerSocialManager> socialSupplier;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Presence-Ping");
        t.setDaemon(true);
        return t;
    });

    private volatile PresenceResponse latestPresence = new PresenceResponse(new ArrayList<PresenceStatusDto>());
    private final CopyOnWriteArrayList<Runnable> presenceListeners = new CopyOnWriteArrayList<Runnable>();
    /** profileId → pmid mapping, built from presence responses. */
    private final ConcurrentHashMap<UUID, UUID> profileIdToPmid = new ConcurrentHashMap<UUID, UUID>();
    /** pmid → profileId mapping. */
    private final ConcurrentHashMap<UUID, UUID> pmidToProfileId = new ConcurrentHashMap<UUID, UUID>();
    private volatile long lastPresencePostNanos = 0L;
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
        this.updatePresence = true;
    }

    public void setHiddenMode(boolean hidden) {
        if (hidden) {
            hiddenModeOfflinePushPending = true;
        } else {
            hiddenModeOfflinePushPending = false;
        }
        tryUpdatePresence();
    }

    public PresenceResponse getLatestPresence() { return latestPresence; }

    public void addPresenceListener(Runnable r) { presenceListeners.add(r); }
    public void removePresenceListener(Runnable r) { presenceListeners.remove(r); }

    public Set<UUID> getInvitedPlayersBatch() { return Collections.unmodifiableSet(invitedPlayersBatch); }

    public void invitePlayer(UUID profileId) {
        if (invitedPlayersBatch.add(profileId)) {
            tryUpdatePresence();
            scheduler.schedule(new Runnable() {
                @Override public void run() { expireHostInvite(profileId); }
            }, 1L, TimeUnit.MINUTES);
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

    public boolean isInvitedPmid(UUID pmid) {
        UUID profileId = getProfileIdFromPmid(pmid);
        return profileId != null && invitedPlayersBatch.contains(profileId);
    }

    public void dismissInviteForPmid(UUID pmid) {
        locallyDismissedInvitePmids.add(pmid);
    }

    public boolean hasDismissedInvite(PresenceStatusDto p) {
        UUID pmid = p.pmid();
        return pmid != null && locallyDismissedInvitePmids.contains(pmid);
    }

    public void rememberPmidMapping(UUID profileId, UUID pmid) {
        if (profileId == null || pmid == null) return;
        profileIdToPmid.put(profileId, pmid);
        pmidToProfileId.put(pmid, profileId);
    }

    public UUID getProfileIdFromPmid(UUID pmid) {
        if (pmid == null) return null;
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

    public String getStatusFromPmid(UUID pmid) {
        if (pmid == null) return null;
        for (PresenceStatusDto p : latestPresence.presence()) {
            if (pmid.equals(p.pmid())) return p.status().name();
        }
        return null;
    }

    public UUID getPmidFromProfileId(UUID profileId) {
        return profileIdToPmid.get(profileId);
    }

    // -------- internal --------

    private boolean shouldRefreshPresence() {
        PlayerSocialManager social = socialSupplier.get();
        if (!social.isFriendListEnabled() || social.getFriends().isEmpty()) return false;
        if (bridge.hiddenMode() && !hiddenModeOfflinePushPending) return false;
        long now = System.nanoTime();
        long since = now - lastPresencePostNanos;
        return (updatePresence && since >= PRESENCE_UPDATE_INTERVAL_NANOS)
                || since >= MAX_PRESENCE_UPDATE_INTERVAL_NANOS;
    }

    private void updatePresence() {
        updatePresence = false;
        lastPresencePostNanos = System.nanoTime();
        PresenceStatus status = getPresenceStatus();
        JoinInfoUpdate joinInfo = getJoinInfoUpdate(status);
        LOGGER.info("[presence] pushing status={} joinInfo={}", status != null ? status.name() : "null", joinInfo);
        // Run presence update asynchronously
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    LOGGER.info("[presence] calling friendsService.presence() on thread {}", Thread.currentThread().getName());
                    PresenceResponse response = friendsService.presence(
                            status != null ? status.name() : "OFFLINE",
                            joinInfo);
                    LOGGER.info("[presence] got response with {} entries", response.presence().size());
                    for (PresenceStatusDto p : response.presence()) {
                        PresenceStatusDto.JoinInfo ji = p.joinInfo();
                        LOGGER.info("[presence][entry] profileId={} pmid={} status={} invited={} joinValue={}",
                                p.profileId(), p.pmid(), p.status(),
                                ji != null && ji.invited(),
                                ji != null ? ji.value() : null);
                    }
                    latestPresence = response;
                    if (bridge.hiddenMode()) {
                        hiddenModeOfflinePushPending = false;
                    }
                    // Update profileId → pmid mapping from every presence entry
                    for (PresenceStatusDto p : response.presence()) {
                        rememberPmidMapping(p.profileId(), p.pmid());
                    }
                    detectStatusChanges(response);
                    clearStaleDismissedInvites();
                    // Detect new invites
                    for (PresenceStatusDto presence : response.presence()) {
                        PresenceStatusDto.JoinInfo ji = presence.joinInfo();
                        if (ji != null && ji.invited()) {
                            for (PlayerSocialManager.PlayerData pd : socialSupplier.get().getFriends()) {
                                if (pd.id().equals(presence.profileId()) && !seenInvites.contains(pd.id())) {
                                    seenInvites.add(pd.id());
                                    if (NotificationPrefs.get().notifyInvite) {
                                        bridge.notifyToast("friend.invite_from", pd.name(), pd.id());
                                    }
                                }
                            }
                        }
                    }
                    for (Runnable r : presenceListeners) {
                        try { r.run(); } catch (Exception e) { LOGGER.error("Presence listener error", e); }
                    }
                } catch (Throwable t) {
                    LOGGER.error("[presence] presence() threw", t);
                }
            }
        }, "Presence-Update").start();
    }

    private void detectStatusChanges(PresenceResponse response) {
        for (PresenceStatusDto dto : response.presence()) {
            UUID profileId = dto.profileId();
            String newStatus = dto.status() != null ? dto.status().name() : "OFFLINE";
            String oldStatus = lastKnownStatus.put(profileId, newStatus);
            // Skip if status hasn't changed, or if transitioning TO offline
            if (newStatus.equals(oldStatus)) continue;
            if ("OFFLINE".equals(newStatus)) continue;
            // Skip the first time we see someone (no previous state)
            if (oldStatus == null) continue;
            // Find friend name
            String friendName = profileId.toString().substring(0, 8);
            for (PlayerSocialManager.PlayerData pd : socialSupplier.get().getFriends()) {
                if (pd.id().equals(profileId)) {
                    friendName = pd.name();
                    break;
                }
            }
            String toastType = "ONLINE".equals(newStatus) ? "friend.status.online"
                    : newStatus.startsWith("PLAYING") ? "friend.status.playing" : "friend.status.changed";
            if (NotificationPrefs.get().notifyStatusChange) {
                bridge.notifyToast(toastType, friendName, profileId);
            }
        }
    }

    private void clearStaleDismissedInvites() {
        Set<UUID> toRemove = new HashSet<UUID>();
        for (UUID pmid : locallyDismissedInvitePmids) {
            boolean stillInvited = false;
            for (PresenceStatusDto p : latestPresence.presence()) {
                PresenceStatusDto.JoinInfo ji = p.joinInfo();
                if (ji != null && pmid.equals(p.pmid()) && ji.invited()) {
                    stillInvited = true;
                    break;
                }
            }
            if (!stillInvited) {
                toRemove.add(pmid);
            }
        }
        locallyDismissedInvitePmids.removeAll(toRemove);
    }

    private PresenceStatus getPresenceStatus() {
        MinecraftBridge.PresenceSharing sharing = bridge.presenceSharing();
        if (sharing == MinecraftBridge.PresenceSharing.NONE) {
            return null;
        }
        if (sharing == MinecraftBridge.PresenceSharing.LIMITED) {
            return PresenceStatus.ONLINE;
        }
        // ALL
        MinecraftBridge.MultiplayerScope scope = bridge.multiplayerScope();
        if (scope == MinecraftBridge.MultiplayerScope.OFF) {
            return PresenceStatus.ONLINE;
        }
        if (scope == MinecraftBridge.MultiplayerScope.LAN) {
            return bridge.inLevel() ? PresenceStatus.PLAYING_HOSTED_SERVER : PresenceStatus.ONLINE;
        }
        // ONLINE
        return PresenceStatus.PLAYING_SERVER;
    }

    private JoinInfoUpdate getJoinInfoUpdate(PresenceStatus status) {
        if (status == null) return null;
        if (bridge.hiddenMode()) return null;
        MinecraftBridge.PresenceSharing sharing = bridge.presenceSharing();
        if (sharing == MinecraftBridge.PresenceSharing.NONE) {
            return null;
        }
        if (sharing == MinecraftBridge.PresenceSharing.LIMITED) {
            Set<UUID> ids = new HashSet<UUID>(invitedPlayersBatch);
            return ids.isEmpty() ? null : new JoinInfoUpdate(null, ids);
        }
        // ALL
        if (status == PresenceStatus.PLAYING_HOSTED_SERVER) {
            return new JoinInfoUpdate(null, new HashSet<UUID>(invitedPlayersBatch));
        }
        return null;
    }

    private void expireHostInvite(UUID profileId) {
        if (!invitedPlayersBatch.remove(profileId)) return;
        tryUpdatePresence();
        PlayerSocialManager social = socialSupplier.get();
        for (PlayerSocialManager.PlayerData p : social.getFriends()) {
            if (p.id().equals(profileId)) {
                if (NotificationPrefs.get().notifyInvite) {
                    bridge.notifyToast("friend.host_invite_expired", p.name(), p.id());
                }
                break;
            }
        }
    }
}
