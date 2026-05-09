package team.starm.starmskyblock.setting;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.manager.ExplosionSettingManager;
import team.starm.starmskyblock.setting.manager.FireSpreadSettingManager;
import team.starm.starmskyblock.setting.manager.GriefSettingManager;
import team.starm.starmskyblock.setting.manager.PvpSettingManager;
import team.starm.starmskyblock.setting.manager.SpawnSettingManager;

/**
 * 岛屿设置协调器
 * 创建并管理所有专门的设置子管理器，注册事件监听器
 */
public class IslandSettingManager extends BaseSettingManager implements Listener {

    private final PvpSettingManager pvpManager;
    private final SpawnSettingManager spawnManager;
    private final FireSpreadSettingManager fireSpreadManager;
    private final GriefSettingManager griefManager;
    private final ExplosionSettingManager explosionManager;

    public IslandSettingManager(IslandManager islandManager, ConfigManager configManager, JavaPlugin plugin) {
        super(islandManager, configManager);

        this.pvpManager = new PvpSettingManager(islandManager, configManager);
        this.spawnManager = new SpawnSettingManager(islandManager, configManager);
        this.fireSpreadManager = new FireSpreadSettingManager(islandManager, configManager);
        this.griefManager = new GriefSettingManager(islandManager, configManager);
        this.explosionManager = new ExplosionSettingManager(islandManager, configManager);

        registerEventListeners(plugin);
    }

    private void registerEventListeners(JavaPlugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(pvpManager, plugin);
        pm.registerEvents(spawnManager, plugin);
        pm.registerEvents(fireSpreadManager, plugin);
        pm.registerEvents(griefManager, plugin);
        pm.registerEvents(explosionManager, plugin);
    }
}
