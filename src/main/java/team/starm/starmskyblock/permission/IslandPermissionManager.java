package team.starm.starmskyblock.permission;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.event.Listener;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.manager.BuildPermissionManager;
import team.starm.starmskyblock.permission.manager.ContainerPermissionManager;
import team.starm.starmskyblock.permission.manager.DoorPermissionManager;
import team.starm.starmskyblock.permission.manager.EntityPermissionManager;
import team.starm.starmskyblock.permission.manager.ItemPermissionManager;
import team.starm.starmskyblock.permission.manager.OtherPermissionManager;
import team.starm.starmskyblock.permission.manager.DropPickupPermissionManager;
import team.starm.starmskyblock.permission.manager.RedstonePermissionManager;
import team.starm.starmskyblock.permission.manager.ToolPermissionManager;
import team.starm.starmskyblock.permission.manager.VehiclePermissionManager;
import team.starm.starmskyblock.permission.manager.WorkblockPermissionManager;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;
import org.bukkit.entity.Player;

/**
 * 岛屿权限协调器
 * 协调所有专门的权限管理器，提供统一的权限检查接口
 */
public class IslandPermissionManager implements Listener {

    protected final IslandManager islandManager;
    protected final ConfigManager configManager;
    private final Map<UUID, Long> lastDenyMessageTime = new HashMap<>();
    private boolean lastCheckWasAreaLocked = false;
    private boolean lastCheckWasPublicArea = false;
    private Island lastAreaLockedIsland = null;
    // 专门的权限管理器实例!
    private final ManagementPermissionManager managementManager;
    private final DropPickupPermissionManager pickupManager;
    private final BuildPermissionManager blockManager;
    private final WorkblockPermissionManager workblockManager;
    private final ContainerPermissionManager containerManager;
    private final RedstonePermissionManager redstoneManager;
    private final DoorPermissionManager doorManager;
    private final VehiclePermissionManager vehicleManager;
    private final ToolPermissionManager toolManager;
    private final ItemPermissionManager itemManager;
    private final EntityPermissionManager entityManager;
    private final OtherPermissionManager otherManager;

    public IslandPermissionManager(IslandManager islandManager, ConfigManager configManager, JavaPlugin plugin) {
        this.islandManager = islandManager;
        this.configManager = configManager;

        // 初始化所有专门的权限管理器
        this.managementManager = new ManagementPermissionManager(islandManager, configManager);
        this.pickupManager = new DropPickupPermissionManager(islandManager, configManager);
        this.blockManager = new BuildPermissionManager(islandManager, configManager);
        this.workblockManager = new WorkblockPermissionManager(islandManager, configManager);
        this.containerManager = new ContainerPermissionManager(islandManager, configManager);
        this.redstoneManager = new RedstonePermissionManager(islandManager, configManager);
        this.doorManager = new DoorPermissionManager(islandManager, configManager);
        this.vehicleManager = new VehiclePermissionManager(islandManager, configManager);
        this.toolManager = new ToolPermissionManager(islandManager, configManager);
        this.itemManager = new ItemPermissionManager(islandManager, configManager);
        this.entityManager = new EntityPermissionManager(islandManager, configManager);
        this.otherManager = new OtherPermissionManager(islandManager, configManager);
    }

    /**
     * 为专门的权限管理器提供的构造函数（不包含JavaPlugin参数）
     */
    public IslandPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        this.islandManager = islandManager;
        this.configManager = configManager;

        // 为专门的权限管理器设置null值
        this.managementManager = null;
        this.pickupManager = null;
        this.blockManager = null;
        this.workblockManager = null;
        this.containerManager = null;
        this.redstoneManager = null;
        this.doorManager = null;
        this.vehicleManager = null;
        this.toolManager = null;
        this.itemManager = null;
        this.entityManager = null;
        this.otherManager = null;
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
     * 根据坐标获取当前位置所属的岛屿对象（静态方法）
     */
    public static Optional<Island> getPlayerCurrentIsland(IslandManager islandManager, Location location) {
        for (Island island : islandManager.getAllIslands()) {
            if (island.isChunkWithinIsland(location.getChunk().getX(), location.getChunk().getZ())) {
                return Optional.of(island);
            }
        }
        return Optional.empty();
    }

    /**
     * 根据坐标获取当前所属岛屿（使用最大范围，用于权限判断）
     */
    public static Optional<Island> getPlayerCurrentIslandMaxRange(IslandManager islandManager, Location location) {
        for (Island island : islandManager.getAllIslands()) {
            if (island.isChunkWithinMaxRange(location.getChunk().getX(), location.getChunk().getZ())) {
                return Optional.of(island);
            }
        }
        return Optional.empty();
    }

    /**
     * 初始化事件监听器（在对象完全构造后调用）
     */
    public void initializeEventListeners(JavaPlugin plugin) {
        registerEventListeners(plugin);
    }

    /**
     * 注册所有权限管理器的事件监听器
     */
    private void registerEventListeners(JavaPlugin plugin) {
        PluginManager pluginManager = plugin.getServer().getPluginManager();

        // 注册所有包含事件处理器的权限管理器
        if (pickupManager != null) {
            pluginManager.registerEvents(pickupManager, plugin);
        }
        if (blockManager != null) {
            pluginManager.registerEvents(blockManager, plugin);
        }
        if (workblockManager != null) {
            pluginManager.registerEvents(workblockManager, plugin);
        }
        if (containerManager != null) {
            pluginManager.registerEvents(containerManager, plugin);
        }
        if (redstoneManager != null) {
            pluginManager.registerEvents(redstoneManager, plugin);
        }
        if (doorManager != null) {
            pluginManager.registerEvents(doorManager, plugin);
        }
        if (vehicleManager != null) {
            pluginManager.registerEvents(vehicleManager, plugin);
        }
        if (toolManager != null) {
            pluginManager.registerEvents(toolManager, plugin);
        }
        if (itemManager != null) {
            pluginManager.registerEvents(itemManager, plugin);
        }
        if (entityManager != null) {
            pluginManager.registerEvents(entityManager, plugin);
        }
        if (otherManager != null) {
            pluginManager.registerEvents(otherManager, plugin);
        }

        // 注册协调器本身的事件监听器（如果有的话）
        pluginManager.registerEvents(this, plugin);
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

        Optional<Island> optIsland = getPlayerCurrentIslandMaxRange(location);

        lastCheckWasAreaLocked = false;
        lastCheckWasPublicArea = false;

        if (optIsland.isEmpty()) {
            lastCheckWasPublicArea = true;
            return false;
        }

        Island island = optIsland.get();
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();

        // 在最大范围内但超出已解锁区域 → 阻止并提示区域未解锁
        if (!island.isChunkWithinIsland(chunkX, chunkZ)) {
            lastCheckWasAreaLocked = true;
            lastAreaLockedIsland = island;
            return false;
        }

        return hasPermission(island, uuid, permission);
    }

    /**
     * 根据坐标获取当前位置所属的岛屿对象（使用当前岛屿范围）
     */
    public Optional<Island> getPlayerCurrentIsland(Location location) {
        return getPlayerCurrentIsland(islandManager, location);
    }

    /**
     * 根据坐标获取当前位置所属的岛屿对象（使用最大岛屿范围，用于权限判断）
     */
    public Optional<Island> getPlayerCurrentIslandMaxRange(Location location) {
        return getPlayerCurrentIslandMaxRange(islandManager, location);
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

    public ManagementPermissionManager getManagementManager() {
        return managementManager;
    }
}
