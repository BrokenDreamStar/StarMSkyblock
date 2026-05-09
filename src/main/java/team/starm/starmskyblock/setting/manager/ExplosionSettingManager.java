package team.starm.starmskyblock.setting.manager;

import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityExplodeEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

public class ExplosionSettingManager extends BaseSettingManager {

    public ExplosionSettingManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        if (entity instanceof Creeper && !checkSetting(event.getLocation(), IslandSetting.CREEPER_EXPLOSION)) {
            event.setCancelled(true);
        } else if ((entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart)
                && !checkSetting(event.getLocation(), IslandSetting.TNT_EXPLOSION)) {
            event.setCancelled(true);
        } else if (entity instanceof Fireball fireball && fireball.getShooter() instanceof Ghast
                && !checkSetting(event.getLocation(), IslandSetting.GHAST_FIREBALL_GRIEF)) {
            event.setCancelled(true);
        } else if (entity instanceof Wither && !checkSetting(event.getLocation(), IslandSetting.WITHER_GRIEF)) {
            event.setCancelled(true);
        } else if (entity instanceof WitherSkull && !checkSetting(event.getLocation(), IslandSetting.WITHER_GRIEF)) {
            event.setCancelled(true);
        }
    }
}
