package dev.msf.friends.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Notification preferences, persisted as .properties file.
 * Java 8 compatible version.
 */
public final class NotificationPrefs {
    private static volatile NotificationPrefs INSTANCE;

    public final boolean enabled;
    public final boolean notifyOnline;
    public final boolean notifyStatus;
    public final boolean notifyInvite;
    public final boolean notifyJoinRequest;

    private NotificationPrefs(boolean enabled, boolean notifyOnline, boolean notifyStatus,
                              boolean notifyInvite, boolean notifyJoinRequest) {
        this.enabled = enabled;
        this.notifyOnline = notifyOnline;
        this.notifyStatus = notifyStatus;
        this.notifyInvite = notifyInvite;
        this.notifyJoinRequest = notifyJoinRequest;
    }

    public static void init(Path configDir) {
        boolean enabled = true;
        boolean notifyOnline = true;
        boolean notifyStatus = true;
        boolean notifyInvite = true;
        boolean notifyJoinRequest = true;

        Path file = configDir.resolve("msf-friends-notifications.properties");
        if (Files.exists(file)) {
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(file)) {
                props.load(is);
                enabled = Boolean.parseBoolean(props.getProperty("notifications.enabled", "true"));
                notifyOnline = Boolean.parseBoolean(props.getProperty("notifications.online", "true"));
                notifyStatus = Boolean.parseBoolean(props.getProperty("notifications.status", "true"));
                notifyInvite = Boolean.parseBoolean(props.getProperty("notifications.invite", "true"));
                notifyJoinRequest = Boolean.parseBoolean(props.getProperty("notifications.join_request", "true"));
            } catch (IOException e) {
                // use defaults
            }
        }
        INSTANCE = new NotificationPrefs(enabled, notifyOnline, notifyStatus, notifyInvite, notifyJoinRequest);
    }

    public static NotificationPrefs get() {
        if (INSTANCE == null) INSTANCE = new NotificationPrefs(true, true, true, true, true);
        return INSTANCE;
    }
}
