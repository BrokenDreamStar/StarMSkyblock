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

public class WorldBorderReflection {

    private static boolean initialized = false;
    private static boolean available = false;

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

    public static void sendWorldBorder(Player player, WorldBorder border) {
        if (border == null) {
            resetWorldBorder(player, player.getWorld());
            return;
        }
        Location center = border.getCenter();
        sendWorldBorder(player, center.getX(), center.getZ(), border.getSize());
    }

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

    private static void sendPacket(Player player, Object nmsWorldBorder) throws Exception {
        Object packet = initializeBorderPacketConstructor.newInstance(nmsWorldBorder);
        Object serverPlayer = playerGetHandle.invoke(player);
        Object connection = connectionField.get(serverPlayer);
        connectionSend.invoke(connection, packet);
    }

    private static void fallback(Player player, double centerX, double centerZ, double size) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setWarningDistance(0);
        player.setWorldBorder(border);
    }

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
