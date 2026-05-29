package dev.msf.friends.social;

import dev.msf.friends.authlib.FriendsService;
import dev.msf.friends.authlib.request.JoinInfoUpdate;
import dev.msf.friends.authlib.response.PresenceResponse;
import dev.msf.friends.authlib.response.PresenceStatus;
import dev.msf.friends.authlib.response.PresenceStatusDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Java 8 port of PresenceHandler.
 *
 * Two pieces are bridged:
 * - The PresenceStatus chosen for the broadcast — depends on bridge settings.
 * - tick() is called by the mod's host once a second.
 */
public class PresenceHandler {
    private static final Logger LOGGER = Logging.get();
    private static final Duration PRESENCE_UPDATE_INTERVAL = Duration.ofSeconds(10L);
    private static final Duration MAX_PRESENCE_UPDATE_INTERVAL = Duration.ofSeconds(60L);

    private final Set<UUID> invitedPlayersBatch = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> locallyDismissedInvitePmids = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> seenInvites = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final ConcurrentHashMap<UUID, UUID> profileIdToPmid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> pmidToProfileId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastKnownStatus = new ConcurrentHashMap<>();
    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final Supplier<PlayerSocialManager> socialSupplier;
    private final ScheduledExecutorService inviteExpiryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Presence-InviteExpiry");
        t.setDaemon(true);
        return t;
    });

    private volatile PresenceResponse latestPresence = new PresenceResponse(new ArrayList<PresenceStatusDto>());
    private final CopyOnWriteArrayList<Runnable> presenceListeners = new CopyOnWriteArrayList<>();
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

    public void invitePlayer(final UUID profileId) {
        if (invitedPlayersBatch.add(profileId)) {
            tryUpdatePresence();
            inviteExpiryScheduler.schedule(new Runnable() {
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
        final PresenceStatus status = getPresenceStatus();
        final JoinInfoUpdate joinInfo = getJoinInfoUpdate(status);
        LOGGER.info("[presence] pushing status={} joinInfo={}", status.name(), joinInfo);
        CompletableFuture.runAsync(new Runnable() {
            @Override public void run() {
                LOGGER.info("[presence] calling friendsService.presence() on thread {}", Thread.currentThread().getName());
                PresenceResponse response;
                try { response = friendsService.presence(status.name(), joinInfo); }
                catch (Throwable t) { LOGGER.error("[presence] presence() threw", t); return; }
                LOGGER.info("[presence] got response with {} entries", response.presence().size());
                bridge.executeOnClientThread(new Runnable() {
                    @Override public void run() {
                        latestPresence = response;
                        if (bridge.hiddenMode()) {
                            hiddenModeOfflinePushPending = false;
                        }
                        for (PresenceStatusDto p : response.presence()) {
                            rememberPmidMapping(p.profileId(), p.pmid());
                        }
                        detectStatusChanges(response);
                        clearStaleDismissedInvites();
                        detectNewAndExpiredInvites(response);
                        presenceListeners.forEach(Runnable::run);
                    }
                });
            }
        });
    }

    private void detectNewAndExpiredInvites(PresenceResponse response) {
        Set<UUID> currentlyInvitedBy = new HashSet<>();
        PlayerSocialManager social = socialSupplier.get();
        for (PresenceStatusDto presence : response.presence()) {
            PresenceStatusDto.JoinInfo ji = presence.joinInfo();
            if (ji != null && ji.invited()) {
                currentlyInvitedBy.add(presence.profileId());
                for (PlayerSocialManager.PlayerData p : social.getFriends()) {
                    if (p.id().equals(presence.profileId()) && !seenInvites.contains(p.id())) {
                        seenInvites.add(p.id());
                        if (NotificationPrefs.get().notifyInvite) {
                            bridge.notifyToast("friend.invite_from", p.name(), p.id());
                        }
                    }
                }
            }
        }
        // Detect expired invites
        Set<UUID> expiredInvites = new HashSet<>(seenInvites);
        expiredInvites.removeAll(currentlyInvitedBy);
        for (UUID expiredId : expiredInvites) {
            seenInvites.remove(expiredId);
            for (PlayerSocialManager.PlayerData p : social.getFriends()) {
                if (p.id().equals(expiredId)) {
                    if (NotificationPrefs.get().notifyInvite) {
                        bridge.notifyToast("friend.invite_expired", p.name(), p.id());
                    }
                }
            }
        }
    }

    private void detectStatusChanges(PresenceResponse response) {
        for (PresenceStatusDto dto : response.presence()) {
            UUID profileId = dto.profileId();
            String newStatus = dto.status().name();
            String oldStatus = lastKnownStatus.put(profileId, newStatus);

            if (newStatus.equals(oldStatus)) continue;
            if ("OFFLINE".equals(newStatus)) continue;
            if (oldStatus == null) continue;

            String friendName = profileId.toString().substring(0, 8);
            for (PlayerSocialManager.PlayerData p : socialSupplier.get().getFriends()) {
                if (p.id().equals(profileId)) {
                    friendName = p.name();
                    break;
                }
            }

            String toastType;
            if ("ONLINE".equals(newStatus)) {
                toastType = "friend.status.online";
            } else if ("PLAYING_OFFLINE".equals(newStatus)) {
                toastType = "friend.status.playing_offline";
            } else if ("PLAYING_HOSTED_SERVER".equals(newStatus)) {
                toastType = "friend.status.hosting";
            } else if ("PLAYING_REALMS".equals(newStatus)) {
                toastType = "friend.status.realms";
            } else if ("PLAYING_SERVER".equals(newStatus)) {
                toastType = "friend.status.server";
            } else {
                toastType = null;
            }

            if (toastType != null) {
                NotificationPrefs prefs = NotificationPrefs.get();
                boolean allowed = "ONLINE".equals(newStatus) ? prefs.notifyOnline : prefs.notifyStatus;
                if (allowed) bridge.notifyToast(toastType, friendName, profileId);
            }
        }
    }

    private void clearStaleDismissedInvites() {
        java.util.Iterator<UUID> it = locallyDismissedInvitePmids.iterator();
        while (it.hasNext()) {
            UUID pmid = it.next();
            boolean stillInvited = false;
            for (PresenceStatusDto p : latestPresence.presence()) {
                if (pmid.equals(p.pmid()) && p.joinInfo() != null && p.joinInfo().invited()) {
                    stillInvited = true;
                    break;
                }
            }
            if (!stillInvited) it.remove();
        }
    }

    private PresenceStatus getPresenceStatus() {
        if (bridge.hiddenMode()) return PresenceStatus.OFFLINE;
        MinecraftBridge.PresenceSharing sharing = bridge.presenceSharing();
        if (sharing == MinecraftBridge.PresenceSharing.NONE) return PresenceStatus.OFFLINE;
        if (sharing == MinecraftBridge.PresenceSharing.LIMITED) return PresenceStatus.ONLINE;
        // ALL
        if (bridge.isHostingP2P()) {
            MinecraftBridge.MultiplayerScope scope = bridge.multiplayerScope();
            if (scope == MinecraftBridge.MultiplayerScope.OFF || scope == MinecraftBridge.MultiplayerScope.LAN) {
                return PresenceStatus.PLAYING_OFFLINE;
            }
            return PresenceStatus.PLAYING_HOSTED_SERVER;
        }
        if (bridge.inLevel()) return PresenceStatus.PLAYING_OFFLINE;
        return PresenceStatus.ONLINE;
    }

    private JoinInfoUpdate getJoinInfoUpdate(PresenceStatus status) {
        if (status == null) return null;
        if (bridge.hiddenMode()) return null;
        MinecraftBridge.PresenceSharing sharing = bridge.presenceSharing();
        if (sharing == MinecraftBridge.PresenceSharing.NONE) return null;
        if (sharing == MinecraftBridge.PresenceSharing.LIMITED) {
            Set<UUID> ids = new HashSet<>(invitedPlayersBatch);
            return ids.isEmpty() ? null : new JoinInfoUpdate(null, ids);
        }
        // ALL
        if (status == PresenceStatus.PLAYING_HOSTED_SERVER) {
            return new JoinInfoUpdate(null, new HashSet<>(invitedPlayersBatch));
        }
        return null;
    }

    public UUID getPmidFromProfileId(UUID profileId) {
        return profileIdToPmid.get(profileId);
    }

    private void expireHostInvite(UUID profileId) {
        if (!invitedPlayersBatch.remove(profileId)) return;
        tryUpdatePresence();
        for (PlayerSocialManager.PlayerData p : socialSupplier.get().getFriends()) {
            if (p.id().equals(profileId)) {
                if (NotificationPrefs.get().notifyInvite) {
                    bridge.notifyToast("friend.host_invite_expired", p.name(), p.id());
                }
                break;
            }
        }
    }
}
