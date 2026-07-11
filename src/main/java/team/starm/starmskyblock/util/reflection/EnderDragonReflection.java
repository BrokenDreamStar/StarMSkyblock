package team.starm.starmskyblock.util.reflection;

import team.starm.starmskyblock.message.MessageUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.World;

/**
 * 末影龙战斗系统反射禁用工具。
 * <p>
 * 通过反射访问 CraftWorld 的 NMS 句柄，将 ServerLevel 的 {@code dragonFight} 字段置空，
 * 从而关闭末地维度的龙战循环（龙蛋生成、末影龙重生等）。
 * <p>
 * 为何用反射：龙战管理器字段属 NMS 内部类型，未在 Paper API 暴露；
 * 直接引用 NMS 类会引入编译期依赖与版本耦合，反射调用可在不同 NMS 版本间保持兼容
 * （字段名缺失时按类型名模糊匹配 {@code dragon}/{@code fight}）。
 */
public class EnderDragonReflection {

    /**
     * 禁用指定世界的末影龙战斗系统。
     * <p>
     * 沿类继承链查找 {@code getHandle()} 取得 NMS ServerLevel，再定位
     * {@code dragonFight} 字段并置为 null。任一步骤失败仅记录警告，不抛出。
     *
     * @param world 目标 Bukkit 世界（通常为末地维度）
     */
    public static void disableDragonFight(World world) {
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
