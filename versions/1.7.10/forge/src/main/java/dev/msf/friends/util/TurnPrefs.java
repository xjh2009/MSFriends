package dev.msf.friends.util;

import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class TurnPrefs {
    private static final Logger LOGGER = Logging.get(TurnPrefs.class);
    private static TurnPrefs INSTANCE;

    public enum IceMode { HYBRID, RELAY_ONLY, PUNCH_ONLY }
    public enum TurnMode { DISABLED, MOJANG_FIRST, MERGILINK_FIRST, REMOVE_MOJANG }

    public IceMode iceMode = IceMode.HYBRID;
    public TurnMode turnMode = TurnMode.MOJANG_FIRST;

    private final File file;

    public TurnPrefs(File configDir) {
        this.file = new File(configDir, "msf_friends_turn.properties");
        load();
    }

    public static void init(File configDir) {
        INSTANCE = new TurnPrefs(configDir);
    }

    public static TurnPrefs get() {
        return INSTANCE;
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("iceMode", iceMode.name());
        props.setProperty("turnMode", turnMode.name());
        try (FileWriter w = new FileWriter(file)) {
            props.store(w, "MSF Friends TURN Preferences");
        } catch (IOException e) {
            LOGGER.error("Failed to save turn prefs", e);
        }
    }

    private void load() {
        if (!file.exists()) return;
        Properties props = new Properties();
        try (FileReader r = new FileReader(file)) {
            props.load(r);
            try { iceMode = IceMode.valueOf(props.getProperty("iceMode", "HYBRID")); } catch (Exception ignored) {}
            try { turnMode = TurnMode.valueOf(props.getProperty("turnMode", "MOJANG_FIRST")); } catch (Exception ignored) {}
        } catch (IOException e) {
            LOGGER.error("Failed to load turn prefs", e);
        }
    }
}
