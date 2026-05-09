package team.starm.starmskyblock.permission;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.event.Listener;

import org.bukkit.entity.Player;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;

/**
 * 权限检查基类
 * 提供 checkPermission / sendDenyMessage 等子管理器共用的方法
 */
public abstract class BasePermissionManager implements Listener {

    protected final IslandManager islandManager;
    protected final ConfigManager configManager;
    protected final Map<UUID, Long> lastDenyMessageTime = new HashMap<>();
    protected boolean lastCheckWasAreaLocked = false;
    protected boolean lastCheckWasPublicArea = false;
    protected Island lastAreaLockedIsland = null;

    public BasePermissionManager(IslandManager islandManager, ConfigManager configManager) {
        this.islandManager = islandManager;
        this.configManager = configManager;
    }

    /**
     * 静态权限检查方法
     */
    public static boolean hasPermission(Island island, UUID uuid, IslandPermission permission) {
        if (island == null) {
            return true;
        }
        return island.hasPermission(uuid, permission);
    }

    /**
     * 根据坐标获取当前位置所属的岛屿对象（委托至 IslandManager）
     */
    public static Optional<Island> getPlayerCurrentIsland(IslandManager islandManager, Location location) {
        return islandManager.getIslandAt(location.getChunk().getX(), location.getChunk().getZ());
    }

    /**
     * 根据坐标获取当前所属岛屿（使用最大范围，用于权限判断）
     */
    public static Optional<Island> getPlayerCurrentIslandMaxRange(IslandManager islandManager, Location location) {
        return islandManager.getIslandAtMaxRange(location.getChunk().getX(), location.getChunk().getZ());
    }

    /**
     * 统一的权限检查方法（使用最大岛屿范围）
     */
    public boolean checkPermission(Location location, UUID uuid, IslandPermission permission) {
        String worldName = location.getWorld().getName();
        if (!worldName.equals(configManager.getWorldNameNormal()) &&
                !worldName.equals(configManager.getWorldNameNether()) &&
                !worldName.equals(configManager.getWorldNameEnd())) {
            return true;
        }

        Optional<Island> optIsland = getPlayerCurrentIslandMaxRange(islandManager, location);

        lastCheckWasAreaLocked = false;
        lastCheckWasPublicArea = false;

        if (optIsland.isEmpty()) {
            lastCheckWasPublicArea = true;
            return false;
        }

        Island island = optIsland.get();
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();

        if (!island.isChunkWithinIsland(chunkX, chunkZ)) {
            lastCheckWasAreaLocked = true;
            lastAreaLockedIsland = island;
            return false;
        }

        return hasPermission(island, uuid, permission);
    }

    /**
     * 权限消息提示（带冷却控制，防止刷屏）
     */
    protected void sendDenyMessage(Player player, IslandPermission permission) {
        long now = System.currentTimeMillis();
        long lastTime = lastDenyMessageTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastTime < configManager.getPermissionMessageCooldown()) {
            return;
        }
        lastDenyMessageTime.put(player.getUniqueId(), now);

        if (lastCheckWasAreaLocked) {
            if (lastAreaLockedIsland != null && lastAreaLockedIsland.getOwnerId().equals(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&e岛屿保护 &f|&c 当前区域未解锁，请升级岛屿！");
            } else if (lastAreaLockedIsland != null) {
                String islandName = lastAreaLockedIsland.getName().isEmpty()
                        ? "岛屿 #" + lastAreaLockedIsland.getId()
                        : lastAreaLockedIsland.getName();
                MessageUtil.sendMessage(player, String.format(
                        "&e岛屿保护 &f|&c 当前区域为 %s 的未解锁区域，没有权限进行操作！", islandName));
            }
            return;
        }

        if (lastCheckWasPublicArea) {
            MessageUtil.sendMessage(player, "&e岛屿保护 &f|&c 公共区域不允许进行操作！");
            return;
        }

        MessageUtil.sendMessage(player, String.format("&e岛屿保护 &f|&c 你没有&e %s &c权限！", permission.getDisplayName()));
    }

    public Optional<Island> getPlayerCurrentIsland(Location location) {
        return getPlayerCurrentIsland(islandManager, location);
    }

    public Optional<Island> getPlayerCurrentIslandMaxRange(Location location) {
        return getPlayerCurrentIslandMaxRange(islandManager, location);
    }
}
