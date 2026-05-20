package dev.msf.friends.util;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class NotificationPrefs {

    private static volatile NotificationPrefs INSTANCE;
    private static volatile @Nullable Path configDir;

    private static final String FILE_NAME = "msf-friends-notif.properties";

    public volatile boolean notifyOnline      = true;
    public volatile boolean notifyStatus      = true;
    public volatile boolean notifyInvite      = true;
    public volatile boolean notifyJoinRequest = true;

    private NotificationPrefs() {
        load();
    }

    public static void init(Path dir) {
        configDir = dir;
    }

    public static NotificationPrefs get() {
        if (INSTANCE == null) {
            synchronized (NotificationPrefs.class) {
                if (INSTANCE == null) INSTANCE = new NotificationPrefs();
            }
        }
        return INSTANCE;
    }

    private Path file() {
        Path dir = configDir;
        if (dir == null) dir = Path.of("config");
        return dir.resolve(FILE_NAME);
    }

    private void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
            notifyOnline      = Boolean.parseBoolean(p.getProperty("notifyOnline",      "true"));
            notifyStatus      = Boolean.parseBoolean(p.getProperty("notifyStatus",      "true"));
            notifyInvite      = Boolean.parseBoolean(p.getProperty("notifyInvite",      "true"));
            notifyJoinRequest = Boolean.parseBoolean(p.getProperty("notifyJoinRequest", "true"));
        } catch (IOException ignored) { }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("notifyOnline",      String.valueOf(notifyOnline));
        p.setProperty("notifyStatus",      String.valueOf(notifyStatus));
        p.setProperty("notifyInvite",      String.valueOf(notifyInvite));
        p.setProperty("notifyJoinRequest", String.valueOf(notifyJoinRequest));
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            try (OutputStream out = Files.newOutputStream(f)) {
                p.store(out, "MSF Friends notification preferences");
            }
        } catch (IOException ignored) { }
    }
}
