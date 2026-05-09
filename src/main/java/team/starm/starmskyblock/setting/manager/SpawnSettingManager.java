package team.starm.starmskyblock.setting.manager;

import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

public class SpawnSettingManager extends BaseSettingManager {

    public SpawnSettingManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        Location location = event.getLocation();

        // 刷怪笼生成
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            if (!checkSetting(location, IslandSetting.SPAWNER_SPAWN)) {
                event.setCancelled(true);
            }
            return;
        }

        // 自然生成
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            if (entity instanceof Monster && !checkSetting(location, IslandSetting.MONSTER_SPAWN)) {
                event.setCancelled(true);
            } else if (entity instanceof Animals && !checkSetting(location, IslandSetting.ANIMAL_SPAWN)) {
                event.setCancelled(true);
            }
        }
    }
}
