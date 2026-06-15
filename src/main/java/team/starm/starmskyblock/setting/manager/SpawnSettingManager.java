package team.starm.starmskyblock.setting.manager;

import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

/**
 * 生物生成设置管理器
 *
 * 监听生物生成事件，根据生成原因（刷怪笼/自然生成/其他）和生物类型（怪物/动物）
 * 分别检查岛屿上对应的设置项，控制岛屿上的生物生成行为。
 *
 * 支持的设置项：
 * - SPAWNER_SPAWN：刷怪笼是否生成生物
 * - MONSTER_SPAWN：是否自然生成敌对生物
 * - ANIMAL_SPAWN：是否自然生成友好生物
 */
public class SpawnSettingManager extends BaseSettingManager {

    public SpawnSettingManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 处理生物生成事件，根据生成原因和生物类型决定是否取消生成
     *
     * 生成原因分类处理：
     * 1. SPAWNER（刷怪笼生成）：统一检查 SPAWNER_SPAWN 设置
     * 2. NATURAL / DEFAULT（自然生成）：根据生物 instanceof 分别检查 MONSTER_SPAWN 或 ANIMAL_SPAWN
     * 3. 其他原因（如繁殖、命名、命令等）不做拦截
     *
     * 使用 HIGH 优先级确保在多数插件之后处理，
     * 并忽略已取消的事件以避免重复处理
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        Location location = event.getLocation();

        // 刷怪笼生成的生物，统一受 SPAWNER_SPAWN 开关控制
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            if (!checkSetting(location, IslandSetting.SPAWNER_SPAWN)) {
                event.setCancelled(true);
            }
            return;
        }

        // 自然生成的生物（从刷怪蛋、结构生成等也属于 NATURAL），
        // 根据生物类型（Monster / Animals）区别对待
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            if ((entity instanceof Monster || entity instanceof Slime) && !checkSetting(location, IslandSetting.MONSTER_SPAWN)) {
                event.setCancelled(true);
            } else if (entity instanceof Animals && !checkSetting(location, IslandSetting.ANIMAL_SPAWN)) {
                event.setCancelled(true);
            }
        }
    }
}
