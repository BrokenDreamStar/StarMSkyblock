package team.starm.starmskyblock.permission;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
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

/**
 * 岛屿权限协调器
 * 创建并管理所有专门的权限子管理器，注册事件监听器
 */
public class IslandPermissionManager extends BasePermissionManager implements Listener {

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
        super(islandManager, configManager);

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

        registerEventListeners(plugin);
    }

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
