package dev.msf.friends.util;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class TurnPrefs {

    public enum IceMode {
        HYBRID,
        RELAY_ONLY,
        PUNCH_ONLY;

        public String displayName() {
            return switch (this) {
                case HYBRID     -> "混合";
                case RELAY_ONLY -> "强制中转";
                case PUNCH_ONLY -> "强制打洞";
            };
        }

        public IceMode next() {
            IceMode[] values = IceMode.values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum TurnMode {
        DISABLED,
        MOJANG_FIRST,
        MERGILINK_FIRST,
        REMOVE_MOJANG;

        public String displayName() {
            return switch (this) {
                case DISABLED         -> "\u4E0D\u542F\u7528";
                case MOJANG_FIRST     -> "\u4F18\u5148Mojang";
                case MERGILINK_FIRST  -> "\u4F18\u5148Mergilink";
                case REMOVE_MOJANG    -> "\u79FB\u9664Mojang";
            };
        }

        public TurnMode next() {
            TurnMode[] values = TurnMode.values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private static volatile TurnPrefs INSTANCE;
    private static volatile @Nullable Path configDir;

    private static final String FILE_NAME = "msf-friends-turn.properties";

    public volatile TurnMode turnMode = TurnMode.DISABLED;
    public volatile IceMode  iceMode  = IceMode.HYBRID;

    private TurnPrefs() {
        load();
    }

    public static void init(Path dir) {
        configDir = dir;
    }

    public static TurnPrefs get() {
        if (INSTANCE == null) {
            synchronized (TurnPrefs.class) {
                if (INSTANCE == null) INSTANCE = new TurnPrefs();
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
            String mode = p.getProperty("turnMode", TurnMode.DISABLED.name());
            try {
                turnMode = TurnMode.valueOf(mode);
            } catch (IllegalArgumentException ignored) {
                turnMode = TurnMode.DISABLED;
            }
            String ice = p.getProperty("iceMode", IceMode.HYBRID.name());
            try {
                iceMode = IceMode.valueOf(ice);
            } catch (IllegalArgumentException ignored) {
                iceMode = IceMode.HYBRID;
            }
        } catch (IOException ignored) { }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("turnMode", turnMode.name());
        p.setProperty("iceMode",  iceMode.name());
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            try (OutputStream out = Files.newOutputStream(f)) {
                p.store(out, "MSF Friends TURN preferences");
            }
        } catch (IOException ignored) { }
    }
}
