package team.starm.starmskyblock.util.reflection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.message.MessageUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 玩家专属世界边界数据包反射发送器。
 * <p>
 * 通过构造 {@code ClientboundInitializeBorderPacket} 直接向单个玩家发送「假」边界，
 * 实现每玩家独立边界（岛屿边界）而不影响真实世界边界或其他玩家。
 * <p>
 * 为何用反射：NMS 数据包与 CraftBukkit 类（CraftPlayer/CraftWorld）属服务端内部实现，
 * 且 CraftBukkit 包名随版本带版本号重定位。直接引用会产生编译期 NMS 依赖并绑定特定版本，
 * 故全部以反射按方法/字段名探测，初始化失败时回退到 Bukkit {@link Player#setWorldBorder}
 * 标准 API。{@code getCraftClass} 还兼容旧版按 {@code MinecraftServer} 包名推断 CraftBukkit 路径。
 */
public class WorldBorderReflection {

    private static boolean initialized = false;
    private static boolean available = false;

    /** 反射缓存：玩家 -> getHandle() -> ServerPlayer.connection -> send(Packet)，及 NMS WorldBorder 与初始化边界包构造器。 */
    private static Method playerGetHandle;
    private static Method worldGetHandle;
    private static Field connectionField;
    private static Method connectionSend;
    private static Constructor<?> worldBorderConstructor;
    private static Method worldBorderSetCenter;
    private static Method worldBorderSetSize;
    private static Method worldBorderSetWarningBlocks;
    private static Method serverLevelGetWorldBorder;
    private static Constructor<?> initializeBorderPacketConstructor;

    /**
     * 向玩家发送自定义虚拟边界（中心 + 半径），不影响真实世界边界。
     * <p>
     * 反射不可用时回退到 {@link #fallback}（Bukkit API 的玩家边界）。
     *
     * @param player  目标玩家
     * @param centerX  边界中心 X
     * @param centerZ  边界中心 Z
     * @param size     边界直径（方块数）
     */
    public static void sendWorldBorder(Player player, double centerX, double centerZ, double size) {
        if (!init()) {
            fallback(player, centerX, centerZ, size);
            return;
        }

        try {
            Object nmsBorder = worldBorderConstructor.newInstance();
            worldBorderSetCenter.invoke(nmsBorder, centerX, centerZ);
            worldBorderSetSize.invoke(nmsBorder, size);
            if (worldBorderSetWarningBlocks != null) {
                worldBorderSetWarningBlocks.invoke(nmsBorder, 0);
            }
            sendPacket(player, nmsBorder);
        } catch (Exception e) {
            MessageUtil.consoleWarn("边界数据包发送失败: " + e.getMessage());
            fallback(player, centerX, centerZ, size);
        }
    }

    /**
     * 向玩家发送既有 {@link WorldBorder} 的中心与尺寸（虚拟边界）。
     * border 为 null 时转而重置玩家边界为所属世界默认值。
     *
     * @param player 目标玩家
     * @param border 取中心与尺寸的来源边界，null 表示重置
     */
    public static void sendWorldBorder(Player player, WorldBorder border) {
        if (border == null) {
            resetWorldBorder(player, player.getWorld());
            return;
        }
        Location center = border.getCenter();
        sendWorldBorder(player, center.getX(), center.getZ(), border.getSize());
    }

    /**
     * 重置玩家边界为指定世界的真实边界（发送该世界 ServerLevel 的 WorldBorder）。
     * <p>
     * 反射不可用或世界为 null 时改用 {@link Player#setWorldBorder(null)} 清除玩家边界。
     *
     * @param player 目标玩家
     * @param world  取真实边界的来源世界
     */
    public static void resetWorldBorder(Player player, World world) {
        if (!init()) {
            player.setWorldBorder(null);
            return;
        }

        if (world == null) {
            player.setWorldBorder(null);
            return;
        }

        try {
            Object serverLevel = worldGetHandle.invoke(world);
            Object worldBorder = serverLevelGetWorldBorder.invoke(serverLevel);
            sendPacket(player, worldBorder);
        } catch (Exception e) {
            player.setWorldBorder(null);
        }
    }

    /** 构造初始化边界包并经玩家 connection 发送出去。 */
    private static void sendPacket(Player player, Object nmsWorldBorder) throws Exception {
        Object packet = initializeBorderPacketConstructor.newInstance(nmsWorldBorder);
        Object serverPlayer = playerGetHandle.invoke(player);
        Object connection = connectionField.get(serverPlayer);
        connectionSend.invoke(connection, packet);
    }

    /** 反射不可用时的同步回退：用 Bukkit API 创建并设置玩家边界。 */
    private static void fallback(Player player, double centerX, double centerZ, double size) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setWarningDistance(0);
        player.setWorldBorder(border);
    }

    /** 懒加载一次性初始化：解析全部 NMS/CraftBukkit 方法与字段句柄并缓存。synchronized 保证只初始化一次。 */
    private static synchronized boolean init() {
        if (initialized) {
            return available;
        }
        initialized = true;

        try {
            Class<?> craftPlayerClass = getCraftClass("entity.CraftPlayer");
            playerGetHandle = findMethodByHierarchy(craftPlayerClass, "getHandle");

            Class<?> serverPlayerClass = playerGetHandle.getReturnType();

            connectionField = findFieldByHierarchy(serverPlayerClass, "connection");
            connectionField.setAccessible(true);

            Class<?> packetListenerClass = connectionField.getType();
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            connectionSend = findMethodByHierarchy(packetListenerClass, "send", packetClass);
            if (connectionSend == null) {
                connectionSend = findMethodByHierarchy(packetListenerClass, "send", findClass("net.minecraft.network.protocol.Packet<?>"));
            }
            if (connectionSend == null) {
                for (Method m : packetListenerClass.getMethods()) {
                    if (m.getName().equals("send") && m.getParameterCount() == 1) {
                        connectionSend = m;
                        break;
                    }
                }
            }
            if (connectionSend == null) {
                throw new NoSuchMethodException("Cannot find send(Packet) method on " + packetListenerClass.getName());
            }
            connectionSend.setAccessible(true);

            Class<?> craftWorldClass = getCraftClass("CraftWorld");
            worldGetHandle = findMethodByHierarchy(craftWorldClass, "getHandle");

            Class<?> serverLevelClass = worldGetHandle.getReturnType();
            serverLevelGetWorldBorder = findMethodByHierarchy(serverLevelClass, "getWorldBorder");
            if (serverLevelGetWorldBorder == null) {
                throw new NoSuchMethodException("Cannot find getWorldBorder() on " + serverLevelClass.getName());
            }

            Class<?> nmsWorldBorderClass = Class.forName("net.minecraft.world.level.border.WorldBorder");
            worldBorderConstructor = nmsWorldBorderClass.getDeclaredConstructor();
            worldBorderConstructor.setAccessible(true);
            worldBorderSetCenter = findMethodByHierarchy(nmsWorldBorderClass, "setCenter", double.class, double.class);
            worldBorderSetSize = findMethodByHierarchy(nmsWorldBorderClass, "setSize", double.class);
            worldBorderSetWarningBlocks = findMethodByHierarchy(nmsWorldBorderClass, "setWarningBlocks", int.class);

            Class<?> initPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket");
            initializeBorderPacketConstructor = initPacketClass.getDeclaredConstructor(nmsWorldBorderClass);

            available = true;
        } catch (Exception e) {
            MessageUtil.consoleWarn("WorldBorderReflection 初始化失败，将回退到 Bukkit API: " + e.getMessage());
            available = false;
        }

        return available;
    }

    /** 按简单名定位 CraftBukkit 类：先试 {@code org.bukkit.craftbukkit.<simple>}，
     *  失败则按 {@code MinecraftServer} 实际包名反推带版本号的 CraftBukkit 路径（兼容旧版本重定位）。 */
    private static Class<?> getCraftClass(String simpleName) throws ClassNotFoundException {
        String base = "org.bukkit.craftbukkit";
        try {
            return Class.forName(base + "." + simpleName);
        } catch (ClassNotFoundException e) {
            try {
                Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
                String serverClassName = serverClass.getName();
                String cbPackage = serverClassName.substring(0, serverClassName.lastIndexOf('.'));
                cbPackage = cbPackage.substring(0, cbPackage.lastIndexOf('.'));
                return Class.forName(cbPackage + "." + simpleName);
            } catch (ClassNotFoundException e2) {
                throw new ClassNotFoundException("Cannot find CraftBukkit class: " + simpleName);
            }
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Method findMethodByHierarchy(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method m = current.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findFieldByHierarchy(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
