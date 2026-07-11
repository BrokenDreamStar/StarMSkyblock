package team.starm.starmskyblock.setting.manager;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

/**
 * 幻翼生成设置管理器
 *
 * 监听生物生成事件，拦截幻翼的自然生成并根据岛屿上的
 * PHANTOM_SPAWN 设置决定是否取消生成。
 */
public class PhantomSpawnSettingManager extends BaseSettingManager {

    public PhantomSpawnSettingManager(IslandManager islandManager, ConfigManager configManager,
                                        PublicAreaConfigManager publicAreaConfig,
                                        LockedAreaConfigManager lockedAreaConfig,
                                        JavaPlugin plugin, SkyblockWorldManager worldManager) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
    }

    /**
     * 处理幻翼的自然生成事件，检查岛屿的 PHANTOM_SPAWN 设置
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhantomSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();

        if (!(entity instanceof Phantom)) {
            return;
        }

        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.DEFAULT) {
            return;
        }

        Location location = event.getLocation();
        if (!checkSetting(location, IslandSetting.PHANTOM_SPAWN)) {
            event.setCancelled(true);
        }
    }
}
