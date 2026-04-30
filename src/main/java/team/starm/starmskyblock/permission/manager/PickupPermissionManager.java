package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 拾取物品权限管理器
 * 处理物品丢弃、拾取和经验球吸取等权限
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
        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.DROP_ITEMS)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.DROP_ITEMS);
        }
    }

    /**
     * 监听实体拾取物品事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.PICKUP_DROPS)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.PICKUP_DROPS);
        }
    }

    /**
     * 监听玩家经验变动事件（防止未授权拾取经验球）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getAmount() > 0
                && !checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.EXP_BALL)) {
            event.setAmount(0);
            sendDenyMessage(player, IslandPermission.EXP_BALL);
        }
    }

    /**
     * 检查是否可以丢弃物品
     */
    public boolean canDropItems(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.DROP_ITEMS);
    }

    /**
     * 检查是否可以拾取物品
     */
    public boolean canPickupDrops(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.PICKUP_DROPS);
    }

    /**
     * 检查是否可以吸取经验球
     */
    public boolean canPickupExpBalls(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.EXP_BALL);
    }
}