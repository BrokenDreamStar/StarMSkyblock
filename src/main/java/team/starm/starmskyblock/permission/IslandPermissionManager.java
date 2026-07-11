package team.starm.starmskyblock.permission;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;
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

/**
 * 岛屿权限协调器
 * <p>
 * 作为所有子权限管理器的统一入口，负责创建 12 个专门的权限子管理器，
 * 并将它们全部注册到 Bukkit 事件总线中。
 * 遵循组合模式：将细粒度的权限检查职责拆分到各个子管理器中，
 * 每个子管理器只关注特定类型的事件（如方块交互、容器交互、生物交互等）。
 * </p>
 */
public class IslandPermissionManager extends BasePermissionManager implements Listener {

    /** 管理权限管理器（删除岛屿、邀请成员、设置权限等） */
    private final ManagementPermissionManager managementManager;
    /** 物品丢弃/拾取权限管理器 */
    private final DropPickupPermissionManager pickupManager;
    /** 方块破坏/建造权限管理器 */
    private final BuildPermissionManager blockManager;
    /** 工作方块权限管理器（工作台、附魔台、铁砧等） */
    private final WorkblockPermissionManager workblockManager;
    /** 容器权限管理器（箱子、熔炉、展示框等） */
    private final ContainerPermissionManager containerManager;
    /** 红石权限管理器（按钮、拉杆、压力板等） */
    private final RedstonePermissionManager redstoneManager;
    /** 门权限管理器（门、栅栏门、活板门） */
    private final DoorPermissionManager doorManager;
    /** 载具权限管理器（矿车、船） */
    private final VehiclePermissionManager vehicleManager;
    /** 工具权限管理器（斧、锹、桶、剪刀等） */
    private final ToolPermissionManager toolManager;
    /** 物品权限管理器（烟花、药水、骨粉、染料等） */
    private final ItemPermissionManager itemManager;
    /** 生物权限管理器（攻击、喂食、骑乘、交易等） */
    private final EntityPermissionManager entityManager;
    /** 杂项权限管理器（耕地、浆果、床、刷怪蛋等） */
    private final OtherPermissionManager otherManager;

    /**
     * 创建所有子权限管理器并注册事件监听器
     * <p>
     * 将所有 12 个子管理器注册到 Bukkit 事件总线。
     * 每个子管理器负责监听和处理特定类型的事件，
     * 实现关注点分离，降低单个类的复杂度。
     * </p>
     *
     * @param islandManager 岛屿管理器
     * @param configManager 配置管理器
     * @param plugin        插件主类实例，用于注册事件
     */
    public IslandPermissionManager(IslandManager islandManager, ConfigManager configManager,
                                        PublicAreaConfigManager publicAreaConfig,
                                        LockedAreaConfigManager lockedAreaConfig,
                                        JavaPlugin plugin, SkyblockWorldManager worldManager) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);

        this.managementManager = new ManagementPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.pickupManager = new DropPickupPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.blockManager = new BuildPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.workblockManager = new WorkblockPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.containerManager = new ContainerPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.redstoneManager = new RedstonePermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.doorManager = new DoorPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.vehicleManager = new VehiclePermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.toolManager = new ToolPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.itemManager = new ItemPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.entityManager = new EntityPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
        this.otherManager = new OtherPermissionManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);

        registerEventListeners(plugin);
    }

    /**
     * 将所有子管理器注册到 Bukkit 事件总线
     * <p>
     * 这样当相关 Minecraft 事件发生时，每个子管理器中的 @EventHandler
     * 方法会被自动调用进行权限检查。
     * </p>
     *
     * @param plugin 插件主类实例
     */
    private void registerEventListeners(JavaPlugin plugin) {
        PluginManager pluginManager = plugin.getServer().getPluginManager();

        pluginManager.registerEvents(managementManager, plugin);
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
    }

    public ManagementPermissionManager getManagementManager() {
        return managementManager;
    }
}
