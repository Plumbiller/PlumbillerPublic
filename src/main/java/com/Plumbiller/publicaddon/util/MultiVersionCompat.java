package com.Plumbiller.publicaddon.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;

public class MultiVersionCompat {

    private static Method gameProfileNameMethod;
    private static Method gameProfileIdMethod;
    private static Method entityWorldMethod;

    /**
     * Safely gets the GameProfile name across versions
     */
    public static String getProfileName(GameProfile profile) {
        if (profile == null)
            return null;
        try {
            if (gameProfileNameMethod == null) {
                gameProfileNameMethod = findMethod(GameProfile.class, String.class, "getName", "name");
            }
            return (String) gameProfileNameMethod.invoke(profile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get GameProfile name", e);
        }
    }

    /**
     * Safely gets the GameProfile ID across versions
     */
    public static UUID getProfileId(GameProfile profile) {
        if (profile == null)
            return null;
        try {
            if (gameProfileIdMethod == null) {
                gameProfileIdMethod = findMethod(GameProfile.class, UUID.class, "getId", "id");
            }
            return (UUID) gameProfileIdMethod.invoke(profile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get GameProfile ID", e);
        }
    }

    /**
     * Safely gets the Entity World across versions
     */
    public static World getEntityWorld(Entity entity) {
        if (entity == null)
            return null;
        try {
            if (entityWorldMethod == null) {
                // "method_5718" is the intermediary name for getWorld, which works in
                // production environments
                entityWorldMethod = findMethod(Entity.class, World.class, "getWorld", "getEntityWorld", "level",
                        "method_5718");
            }
            return (World) entityWorldMethod.invoke(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Entity World", e);
        }
    }

    private static Method findMethod(Class<?> clazz, Class<?> returnType, String... names) {
        // 1. Try exact names (Yarn, Intermediary, etc.)
        for (String name : names) {
            try {
                Method m = clazz.getMethod(name);
                // Verify return type if specified
                if (returnType == null || returnType.isAssignableFrom(m.getReturnType())) {
                    // Make accessible just in case
                    m.setAccessible(true);
                    return m;
                }
            } catch (NoSuchMethodException e) {
                // Continue searching
            }
        }

        // 2. Try simple signature matching (Public, 0 args, matches return type)
        // This is a robust fallback for handling obfuscation or unmapped environments
        if (returnType != null) {
            for (Method m : clazz.getMethods()) {
                if (Modifier.isPublic(m.getModifiers()) &&
                        m.getParameterCount() == 0 &&
                        returnType.isAssignableFrom(m.getReturnType())) {
                    return m;
                }
            }
        }

        throw new RuntimeException("Could not find method returning " + returnType.getSimpleName() + " in "
                + clazz.getSimpleName() + " with candidates " + Arrays.toString(names));
    }
}
