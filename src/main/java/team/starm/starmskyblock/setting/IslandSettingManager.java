package team.starm.starmskyblock.setting;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.manager.ExplosionSettingManager;
import team.starm.starmskyblock.setting.manager.FireSpreadSettingManager;
import team.starm.starmskyblock.setting.manager.GriefSettingManager;
import team.starm.starmskyblock.setting.manager.PhantomSpawnSettingManager;
import team.starm.starmskyblock.setting.manager.PvpSettingManager;
import team.starm.starmskyblock.setting.manager.SpawnSettingManager;

/**
 * 岛屿设置的总协调器
 *
 * 负责实例化所有功能子管理器（PVP、生成、火势蔓延、破坏、爆炸），
 * 并将它们全部注册到 Bukkit 事件总线中。
 *
 * 设计上采用"委托-子管理器"模式：
 * - IslandSettingManager 本身继承 BaseSettingManager 提供通用的辅助方法
 * - 每个具体功能由一个独立的子管理器实现，职责单一，便于维护和扩展
 * - 所有子管理器共用同一个 islandManager 和 configManager 实例
 */
public class IslandSettingManager extends BaseSettingManager implements Listener {

    /** PVP 战斗相关的设置管理器 */
    private final PvpSettingManager pvpManager;
    /** 生物生成相关的设置管理器（动物、怪物、刷怪笼） */
    private final SpawnSettingManager spawnManager;
    /** 火势蔓延相关的设置管理器 */
    private final FireSpreadSettingManager fireSpreadManager;
    /** 实体破坏方块相关的设置管理器（末影人、凋灵） */
    private final GriefSettingManager griefManager;
    /** 爆炸相关的设置管理器（苦力怕、TNT、恶魂、凋灵头颅） */
    private final ExplosionSettingManager explosionManager;
    /** 幻翼生成相关的设置管理器 */
    private final PhantomSpawnSettingManager phantomSpawnManager;

    public IslandSettingManager(IslandManager islandManager, ConfigManager configManager,
                                     PublicAreaConfigManager publicAreaConfig,
                                     LockedAreaConfigManager lockedAreaConfig, JavaPlugin plugin) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig);

        // 创建各个功能子管理器，每个管理器负责监听一类事件
        this.pvpManager = new PvpSettingManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
        this.spawnManager = new SpawnSettingManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
        this.fireSpreadManager = new FireSpreadSettingManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
        this.griefManager = new GriefSettingManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
        this.explosionManager = new ExplosionSettingManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
        this.phantomSpawnManager = new PhantomSpawnSettingManager(islandManager, configManager, publicAreaConfig, lockedAreaConfig);

        // 将所有子管理器注册为事件监听器
        registerEventListeners(plugin);
    }

    /**
     * 将所有子管理器注册到 Bukkit 事件总线
     * 只有注册后，子管理器中标注 @EventHandler 的方法才会被调用
     */
    private void registerEventListeners(JavaPlugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(pvpManager, plugin);
        pm.registerEvents(spawnManager, plugin);
        pm.registerEvents(fireSpreadManager, plugin);
        pm.registerEvents(griefManager, plugin);
        pm.registerEvents(explosionManager, plugin);
        pm.registerEvents(phantomSpawnManager, plugin);
    }
}
