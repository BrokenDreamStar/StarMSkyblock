package team.starm.starmskyblock.permission.manager;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionManager;

/**
 * 拾取权限管理器
 */
public class PickupPermissionManager extends IslandPermissionManager {

    public PickupPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家丢弃物品事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.ITEM_DROP)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ITEM_DROP);
        }
    }

    /**
     * 监听实体拾取物品事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.ITEM_PICKUP)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ITEM_PICKUP);
        }
    }

    /**
     * 监听拾取经验球事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickupExp(PlayerPickupExperienceEvent event) {
        Player player = event.getPlayer();
        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.EXP_PICKUP)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.EXP_PICKUP);
        }
    }
}