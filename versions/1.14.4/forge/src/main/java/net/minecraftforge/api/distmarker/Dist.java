package net.minecraftforge.api.distmarker;

/**
 * Stub for Forge 1.14.4's Dist enum — missing from the mavenizer output.
 * The real class is in the Forge universal jar but was not merged.
 */
public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public Dist opposite() {
        return this == CLIENT ? DEDICATED_SERVER : CLIENT;
    }
}
