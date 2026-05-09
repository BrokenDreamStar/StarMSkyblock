package team.starm.starmskyblock.setting.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Entity;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.setting.BaseSettingManager;
import team.starm.starmskyblock.setting.IslandSetting;

public class PvpSettingManager extends BaseSettingManager {

    public PvpSettingManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        Player damager = getPlayerDamager(event.getDamager());
        if (damager == null) {
            return;
        }

        Location location = target.getLocation();
        if (!checkSetting(location, IslandSetting.PVP)) {
            event.setCancelled(true);
            MessageUtil.sendMessage(damager, "&e岛屿保护 &f|&c 该岛屿已禁用PVP！");
        }
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
