package team.starm.starmskyblock.util.reflection;

import team.starm.starmskyblock.message.MessageUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class EnderDragonReflection {

    public static void disableDragonFight(org.bukkit.World world) {
        try {
            Method getHandle = null;
            Class<?> clazz = world.getClass();
            while (clazz != null && getHandle == null) {
                try {
                    getHandle = clazz.getDeclaredMethod("getHandle");
                } catch (NoSuchMethodException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }

            if (getHandle != null) {
                getHandle.setAccessible(true);
                Object serverLevel = getHandle.invoke(world);

                Field dragonFightField = null;
                try {
                    dragonFightField = serverLevel.getClass().getDeclaredField("dragonFight");
                } catch (NoSuchFieldException e) {
                    for (Field f : serverLevel.getClass().getDeclaredFields()) {
                        String typeName = f.getType().getSimpleName().toLowerCase();
                        if (typeName.contains("dragon") || typeName.contains("fight")) {
                            dragonFightField = f;
                            break;
                        }
                    }
                }

                if (dragonFightField != null) {
                    dragonFightField.setAccessible(true);
                    dragonFightField.set(serverLevel, null);
                }
            }
        } catch (Exception e) {
            MessageUtil.consoleWarn("无法通过反射禁用末地龙战系统: " + e.getMessage());
        }
    }
}
