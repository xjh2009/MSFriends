package dev.msf.friends.social;

import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.response.PresenceResponse;
import com.mojang.authlib.yggdrasil.response.PresenceStatus;
import com.mojang.authlib.yggdrasil.response.PresenceStatusDto;
import dev.msf.friends.bridge.MinecraftBridge;
import dev.msf.friends.util.Logging;
import dev.msf.friends.util.NotificationPrefs;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * PresenceHandler - Java 8 compatible version.
 * Uses ScheduledExecutorService instead of CompletableFuture.delayedExecutor.
 * Uses regular ConcurrentHashMap instead of ConcurrentHashMap.newKeySet().
 */
public class PresenceHandler {
    private static final Logger LOGGER = Logging.get();
    private static final long PRESENCE_UPDATE_INTERVAL_SECONDS = 10L;
    private static final long MAX_PRESENCE_UPDATE_INTERVAL_SECONDS = 60L;
    private static final long INVITE_EXPIRE_SECONDS = 60L;

    private final Set<UUID> invitedPlayersBatch = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> locallyDismissedInvitePmids = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> seenInvites = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final ConcurrentHashMap<UUID, UUID> profileIdToPmid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> pmidToProfileId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastKnownStatus = new ConcurrentHashMap<>();
    private final MinecraftBridge bridge;
    private final FriendsService friendsService;
    private final Supplier<PlayerSocialManager> socialSupplier;
    private final ScheduledExecutorService scheduler;

    private volatile PresenceResponse latestPresence = new PresenceResponse(Collections.<PresenceStatusDto>emptyList());
    private final CopyOnWriteArrayList<Runnable> presenceListeners = new CopyOnWriteArrayList<>();
    private volatile long lastPresencePostNanos = 0L;
    private volatile boolean updatePresence = true;
    private volatile boolean hiddenModeOfflinePushPending;

    public PresenceHandler(MinecraftBridge bridge, FriendsService friendsService,
                           Supplier<PlayerSocialManager> socialSupplier) {
        this.bridge = bridge;
        this.friendsService = friendsService;
        this.socialSupplier = socialSupplier;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Presence-Updater");
                t.setDaemon(true);
                return t;
            }
        });
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
        this.lastPresencePostNanos = 0L;
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
            scheduler.schedule(new Runnable() {
                @Override public void run() { expireHostInvite(profileId); }
            }, INVITE_EXPIRE_SECONDS, TimeUnit.SECONDS);
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
        UUID pmid = presence.profileId(); // Use profileId as fallback
        return locallyDismissedInvitePmids.contains(pmid);
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
        for (PresenceStatusDto p : latestPresence.statuses()) {
            if (pmid.equals(p.profileId())) {
                rememberPmidMapping(p.profileId(), p.profileId());
                return p.profileId();
            }
        }
        return null;
    }

    public boolean isOnline(UUID profileId) {
        String status = lastKnownStatus.get(profileId);
        return status != null && !"OFFLINE".equals(status);
    }

    @Nullable
    public UUID getPmidFromProfileId(UUID profileId) {
        return profileIdToPmid.get(profileId);
    }

    private boolean shouldRefreshPresence() {
        PlayerSocialManager social = socialSupplier.get();
        if (!social.isOnlineMode() || social.getFriends().isEmpty()) return false;
        if (bridge.hiddenMode() && !hiddenModeOfflinePushPending) return false;
        long now = System.nanoTime();
        long elapsed = now - lastPresencePostNanos;
        return (updatePresence && elapsed >= TimeUnit.SECONDS.toNanos(PRESENCE_UPDATE_INTERVAL_SECONDS))
                || elapsed >= TimeUnit.SECONDS.toNanos(MAX_PRESENCE_UPDATE_INTERVAL_SECONDS);
    }

    private void updatePresence() {
        updatePresence = false;
        lastPresencePostNanos = System.nanoTime();
        bridge.executeOnClientThread(new Runnable() {
            @Override public void run() {
                try {
                    PresenceResponse response = friendsService.getPresence();
                    bridge.executeOnClientThread(new Runnable() {
                        @Override public void run() {
                            latestPresence = response;
                            if (bridge.hiddenMode()) {
                                hiddenModeOfflinePushPending = false;
                            }
                            for (PresenceStatusDto p : response.statuses()) {
                                rememberPmidMapping(p.profileId(), p.profileId());
                            }
                            detectStatusChanges(response);
                            presenceListeners.forEach(Runnable::run);
                        }
                    });
                } catch (Exception e) {
                    LOGGER.warn("[presence] update failed", e);
                }
            }
        });
    }

    private void detectStatusChanges(PresenceResponse response) {
        for (PresenceStatusDto dto : response.statuses()) {
            UUID profileId = dto.profileId();
            String newStatus = dto.status().name();
            String oldStatus = lastKnownStatus.put(profileId, newStatus);
            if (newStatus.equals(oldStatus)) continue;
            if ("OFFLINE".equals(newStatus)) continue;
            if (oldStatus == null) continue;
            String friendName = "";
            for (PlayerSocialManager.PlayerData p : socialSupplier.get().getFriends()) {
                if (p.id().equals(profileId)) { friendName = p.name(); break; }
            }
            if (friendName.isEmpty()) friendName = profileId.toString().substring(0, 8);
            NotificationPrefs prefs = NotificationPrefs.get();
            boolean allowed = "ONLINE".equals(newStatus) ? prefs.notifyOnline : prefs.notifyStatus;
            if (allowed) bridge.notifyToast("friend.status." + newStatus.toLowerCase(), friendName, profileId);
        }
    }

    private void expireHostInvite(UUID profileId) {
        if (!invitedPlayersBatch.remove(profileId)) return;
        tryUpdatePresence();
        NotificationPrefs prefs = NotificationPrefs.get();
        if (prefs.notifyInvite) {
            for (PlayerSocialManager.PlayerData p : socialSupplier.get().getFriends()) {
                if (p.id().equals(profileId)) {
                    bridge.notifyToast("friend.host_invite_expired", p.name(), p.id());
                    break;
                }
            }
        }
    }
}
