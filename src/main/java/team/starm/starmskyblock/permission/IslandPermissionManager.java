package team.starm.starmskyblock.permission;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.event.Listener;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.manager.BlockPermissionManager;
import team.starm.starmskyblock.permission.manager.ContainerPermissionManager;
import team.starm.starmskyblock.permission.manager.DoorPermissionManager;
import team.starm.starmskyblock.permission.manager.EntityPermissionManager;
import team.starm.starmskyblock.permission.manager.ItemPermissionManager;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.permission.manager.OtherPermissionManager;
import team.starm.starmskyblock.permission.manager.PickupPermissionManager;
import team.starm.starmskyblock.permission.manager.RedstonePermissionManager;
import team.starm.starmskyblock.permission.manager.ToolPermissionManager;
import team.starm.starmskyblock.permission.manager.VehiclePermissionManager;
import team.starm.starmskyblock.permission.manager.WorkblockPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;
import org.bukkit.entity.Player;

/**
 * 岛屿权限协调器
 * 协调所有专门的权限管理器，提供统一的权限检查接口
 */
public class IslandPermissionManager implements Listener {

    protected final IslandManager islandManager;
    protected final ConfigManager configManager;

    // 专门的权限管理器实例
    private final ManagementPermissionManager managementManager;
    private final PickupPermissionManager pickupManager;
    private final BlockPermissionManager blockManager;
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
        this.pickupManager = new PickupPermissionManager(islandManager, configManager);
        this.blockManager = new BlockPermissionManager(islandManager, configManager);
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
     * 判断玩家是否是岛屿的拥有者或成员
     */
    public static boolean hasPermission(Island island, UUID uuid) {
        return island.getOwnerId().equals(uuid) || island.getMembers().containsKey(uuid);
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
     * 检查某个位置是否在受保护的岛屿内（静态方法）
     */
    public static boolean isPlayerOnProtectedIsland(IslandManager islandManager, Location location) {
        return getPlayerCurrentIsland(islandManager, location).isPresent();
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
        pluginManager.registerEvents(pickupManager, plugin);
        pluginManager.registerEvents(blockManager, plugin);
        pluginManager.registerEvents(workblockManager, plugin);
        pluginManager.registerEvents(containerManager, plugin);
        pluginManager.registerEvents(redstoneManager, plugin);
        pluginManager.registerEvents(doorManager, plugin);
        pluginManager.registerEvents(vehicleManager, plugin);
        pluginManager.registerEvents(toolManager, plugin);
        pluginManager.registerEvents(itemManager, plugin);
        pluginManager.registerEvents(entityManager, plugin);
        pluginManager.registerEvents(otherManager, plugin);

        // 注册协调器本身的事件监听器（如果有的话）
        pluginManager.registerEvents(this, plugin);
    }

    /**
     * 统一的权限检查方法
     */
    public boolean checkPermission(Location location, UUID uuid, IslandPermission permission) {
        String worldName = location.getWorld().getName();
        if (!worldName.equals(configManager.getWorldNameNormal()) &&
                !worldName.equals(configManager.getWorldNameNether()) &&
                !worldName.equals(configManager.getWorldNameEnd())) {
            return true;
        }

        Optional<Island> optIsland = getPlayerCurrentIsland(location);
        return optIsland.map(island -> hasPermission(island, uuid, permission)).orElse(true);
    }

    /**
     * 根据坐标获取当前位置所属的岛屿对象
     */
    public Optional<Island> getPlayerCurrentIsland(Location location) {
        return getPlayerCurrentIsland(islandManager, location);
    }

    /**
     * 获取管理权限管理器
     */
    public ManagementPermissionManager getManagementManager() {
        return managementManager;
    }

    /**
     * 获取拾取权限管理器
     */
    public PickupPermissionManager getPickupManager() {
        return pickupManager;
    }

    /**
     * 获取方块权限管理器
     */
    public BlockPermissionManager getBlockManager() {
        return blockManager;
    }

    /**
     * 获取工作方块权限管理器
     */
    public WorkblockPermissionManager getWorkblockManager() {
        return workblockManager;
    }

    /**
     * 获取容器权限管理器
     */
    public ContainerPermissionManager getContainerManager() {
        return containerManager;
    }

    /**
     * 获取红石权限管理器
     */
    public RedstonePermissionManager getRedstoneManager() {
        return redstoneManager;
    }

    /**
     * 获取门权限管理器
     */
    public DoorPermissionManager getDoorManager() {
        return doorManager;
    }

    /**
     * 获取载具权限管理器
     */
    public VehiclePermissionManager getVehicleManager() {
        return vehicleManager;
    }

    /**
     * 获取工具权限管理器
     */
    public ToolPermissionManager getToolManager() {
        return toolManager;
    }

    /**
     * 获取物品权限管理器
     */
    public ItemPermissionManager getItemManager() {
        return itemManager;
    }

    /**
     * 获取生物权限管理器
     */
    public EntityPermissionManager getEntityManager() {
        return entityManager;
    }

    /**
     * 获取其它权限管理器
     */
    public OtherPermissionManager getOtherManager() {
        return otherManager;
    }

    /**
     * 判断玩家是否具有管理成员的权限
     */
    public boolean canManageMembers(Island island, UUID uuid) {
        return managementManager.canManageMembers(island, uuid);
    }

    /**
     * 权限消息提示
     */
    protected void sendDenyMessage(Player player, IslandPermission permission) {
        MessageUtil.sendMessage(player, String.format("&e岛屿保护 &f|&c 你没有&e %s &c权限！", permission.getDisplayName()));
    }
}