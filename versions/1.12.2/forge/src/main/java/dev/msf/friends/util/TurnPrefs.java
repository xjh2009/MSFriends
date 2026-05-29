package dev.msf.friends.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * TURN/ICE configuration preferences. Java 8 compatible.
 */
public final class TurnPrefs {
    private static volatile boolean turnEnabled = true;
    private static volatile boolean mergilinkEnabled = true;

    private TurnPrefs() {}

    public static void init(Path configDir) {
        Path file = configDir.resolve("msf-friends-turn.properties");
        if (Files.exists(file)) {
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(file)) {
                props.load(is);
                turnEnabled = Boolean.parseBoolean(props.getProperty("turn.enabled", "true"));
                mergilinkEnabled = Boolean.parseBoolean(props.getProperty("mergilink.enabled", "true"));
            } catch (IOException e) {
                // use defaults
            }
        }
    }

    public static boolean isTurnEnabled() { return turnEnabled; }
    public static boolean isMergilinkEnabled() { return mergilinkEnabled; }
}
