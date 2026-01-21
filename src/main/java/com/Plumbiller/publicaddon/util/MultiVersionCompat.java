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

    public static World getEntityWorld(Entity entity) {
        if (entity == null)
            return null;
        try {
            if (entityWorldMethod == null) {
                entityWorldMethod = findMethod(Entity.class, World.class, "getWorld", "getEntityWorld", "level",
                        "method_5718");
            }
            return (World) entityWorldMethod.invoke(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Entity World", e);
        }
    }

    private static Method findMethod(Class<?> clazz, Class<?> returnType, String... names) {
        for (String name : names) {
            try {
                Method m = clazz.getMethod(name);
                if (returnType == null || returnType.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (NoSuchMethodException e) {
            }
        }

        if (returnType != null) {
            for (Method m : clazz.getMethods()) {
                if (Modifier.isPublic(m.getModifiers()) &&
                        m.getParameterCount() == 0 &&
                        returnType.isAssignableFrom(m.getReturnType())) {
                    return m;
                }
            }
        }

        String returnTypeName = returnType != null ? returnType.getSimpleName() : "any type";
        throw new RuntimeException("Could not find method returning " + returnTypeName + " in "
                + clazz.getSimpleName() + " with candidates " + Arrays.toString(names));
    }
}
