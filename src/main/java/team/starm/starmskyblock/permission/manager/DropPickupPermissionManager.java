package team.starm.starmskyblock.permission.manager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;

/**
 * 物品丢弃/拾取权限管理器
 * <p>
 * 监听玩家丢弃物品和拾取物品的事件，根据所在岛屿的权限配置
 * 判断是否允许操作。使用 EventPriority.HIGH 优先级确保在其他
 * 插件处理后进行权限检查，同时忽略已被取消的事件。
 * </p>
 */
public class DropPickupPermissionManager extends BasePermissionManager {

    public DropPickupPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家丢弃物品事件
     * <p>
     * 当玩家在岛屿区域内丢弃物品时，检查是否拥有 ITEM_DROP 权限。
     * 若无权限则取消事件并发送提示消息。
     * </p>
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
     * <p>
     * 过滤出玩家实体的拾取操作进行检查。生物拾取物品不受此限制。
     * 检查玩家是否拥有 ITEM_PICKUP 权限。
     * </p>
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

}