package team.starm.starmskyblock.setting.manager;

import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

public class GriefSettingManager extends BaseSettingManager {

    public GriefSettingManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();

        if (entity instanceof Enderman && !checkSetting(event.getBlock().getLocation(), IslandSetting.ENDERMAN_GRIEF)) {
            event.setCancelled(true);
        } else if (entity instanceof Wither && !checkSetting(event.getBlock().getLocation(), IslandSetting.WITHER_GRIEF)) {
            event.setCancelled(true);
        }
    }
}
