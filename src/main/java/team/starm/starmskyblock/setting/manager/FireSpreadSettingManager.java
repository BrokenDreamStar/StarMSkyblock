package team.starm.starmskyblock.setting.manager;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockSpreadEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

public class FireSpreadSettingManager extends BaseSettingManager {

    public FireSpreadSettingManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE
                && event.getSource().getType() != Material.SOUL_FIRE) {
            return;
        }

        if (!checkSetting(event.getBlock().getLocation(), IslandSetting.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!checkSetting(event.getBlock().getLocation(), IslandSetting.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }
}
