package dev.msf.friends.util;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Method;

/**
 * Helper to call the version-appropriate profile property filling method.
 *
 * Authlib 4.x (MC 1.20.1): MinecraftSessionService.fillProfileProperties(GameProfile, boolean)
 * Authlib 7.x (MC 1.21+):  MinecraftSessionService.fetchProfile(UUID, boolean) → ProfileResult
 */
public final class ProfileHelper {

    private static final Method FILL_PROFILE_METHOD;

    static {
        Method m = null;
        try {
            // Try authlib 4.x method first (1.20.1 runtime)
            m = Class.forName("com.mojang.authlib.minecraft.MinecraftSessionService")
                    .getMethod("fillProfileProperties", GameProfile.class, boolean.class);
        } catch (NoSuchMethodException | ClassNotFoundException e1) {
            try {
                // Fallback: authlib 7.x uses fetchProfile which returns ProfileResult
                Method fetchProfile = Class.forName("com.mojang.authlib.minecraft.MinecraftSessionService")
                        .getMethod("fetchProfile", java.util.UUID.class, boolean.class);
                m = fetchProfile;
            } catch (Exception e2) {
                // Neither method available
            }
        }
        FILL_PROFILE_METHOD = m;
    }

    private ProfileHelper() {}

    /**
     * Fill profile properties using whatever method is available in the runtime authlib.
     *
     * @param sessionService the session service (MinecraftSessionService)
     * @param profile        template profile with at least UUID and name
     * @return filled profile, or null on failure
     */
    public static GameProfile fillProfile(Object sessionService, GameProfile profile) {
        if (FILL_PROFILE_METHOD == null || sessionService == null) return null;
        try {
            String methodName = FILL_PROFILE_METHOD.getName();
            if ("fillProfileProperties".equals(methodName)) {
                // authlib 4.x: returns GameProfile directly
                return (GameProfile) FILL_PROFILE_METHOD.invoke(sessionService, profile, true);
            } else if ("fetchProfile".equals(methodName)) {
                // authlib 7.x: returns ProfileResult which has .profile() method
                Object result = FILL_PROFILE_METHOD.invoke(sessionService, profile.id(), true);
                if (result != null) {
                    Method profileMethod = result.getClass().getMethod("profile");
                    return (GameProfile) profileMethod.invoke(result);
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
        return null;
    }
}
