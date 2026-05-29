package dev.msf.friends.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class TurnPrefs {

    public enum IceMode {
        HYBRID, RELAY_ONLY, PUNCH_ONLY;
        public String displayName() {
            if (this == HYBRID) return "Hybrid";
            if (this == RELAY_ONLY) return "Relay Only";
            return "Punch Only";
        }
        public IceMode next() {
            IceMode[] values = IceMode.values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum TurnMode {
        DISABLED, MOJANG_FIRST, MERGILINK_FIRST, REMOVE_MOJANG;
        public String displayName() {
            if (this == DISABLED) return "Disabled";
            if (this == MOJANG_FIRST) return "Mojang First";
            if (this == MERGILINK_FIRST) return "Mergilink First";
            return "Remove Mojang";
        }
        public TurnMode next() {
            TurnMode[] values = TurnMode.values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private static volatile TurnPrefs INSTANCE;
    private static volatile Path configDir;
    private static final String FILE_NAME = "msf-friends-turn.properties";

    public volatile TurnMode turnMode = TurnMode.DISABLED;
    public volatile IceMode iceMode = IceMode.HYBRID;

    private TurnPrefs() { load(); }

    public static void init(Path dir) { configDir = dir; }

    public static TurnPrefs get() {
        if (INSTANCE == null) {
            synchronized (TurnPrefs.class) {
                if (INSTANCE == null) INSTANCE = new TurnPrefs();
            }
        }
        return INSTANCE;
    }

    private Path file() {
        Path dir = configDir != null ? configDir : java.nio.file.Paths.get("config");
        return dir.resolve(FILE_NAME);
    }

    private void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
            try { turnMode = TurnMode.valueOf(p.getProperty("turnMode", TurnMode.DISABLED.name())); }
            catch (IllegalArgumentException e) { turnMode = TurnMode.DISABLED; }
            try { iceMode = IceMode.valueOf(p.getProperty("iceMode", IceMode.HYBRID.name())); }
            catch (IllegalArgumentException e) { iceMode = IceMode.HYBRID; }
        } catch (IOException ignored) {}
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("turnMode", turnMode.name());
        p.setProperty("iceMode", iceMode.name());
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            try (OutputStream out = Files.newOutputStream(f)) {
                p.store(out, "MSF Friends TURN preferences");
            }
        } catch (IOException ignored) {}
    }
}
