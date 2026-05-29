package dev.msf.friends.util;

import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class NotificationPrefs {
    private static final Logger LOGGER = Logging.get(NotificationPrefs.class);
    private static NotificationPrefs INSTANCE;

    public boolean notifyOnline = true;
    public boolean notifyStatus = true;
    public boolean notifyStatusChange = true;
    public boolean notifyInvite = true;
    public boolean notifyJoinRequest = true;

    private final File file;

    public NotificationPrefs(File configDir) {
        this.file = new File(configDir, "msf_friends_notifications.properties");
        load();
    }

    public static void init(File configDir) {
        INSTANCE = new NotificationPrefs(configDir);
    }

    public static NotificationPrefs get() {
        return INSTANCE;
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("notifyOnline", String.valueOf(notifyOnline));
        props.setProperty("notifyStatus", String.valueOf(notifyStatus));
        props.setProperty("notifyStatusChange", String.valueOf(notifyStatusChange));
        props.setProperty("notifyInvite", String.valueOf(notifyInvite));
        props.setProperty("notifyJoinRequest", String.valueOf(notifyJoinRequest));
        try (FileWriter w = new FileWriter(file)) {
            props.store(w, "MSF Friends Notification Preferences");
        } catch (IOException e) {
            LOGGER.error("Failed to save notification prefs", e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        Properties props = new Properties();
        try (FileReader r = new FileReader(file)) {
            props.load(r);
            notifyOnline = Boolean.parseBoolean(props.getProperty("notifyOnline", "true"));
            notifyStatus = Boolean.parseBoolean(props.getProperty("notifyStatus", "true"));
            notifyStatusChange = Boolean.parseBoolean(props.getProperty("notifyStatusChange", "true"));
            notifyInvite = Boolean.parseBoolean(props.getProperty("notifyInvite", "true"));
            notifyJoinRequest = Boolean.parseBoolean(props.getProperty("notifyJoinRequest", "true"));
        } catch (IOException e) {
            LOGGER.error("Failed to load notification prefs", e);
        }
    }
}
