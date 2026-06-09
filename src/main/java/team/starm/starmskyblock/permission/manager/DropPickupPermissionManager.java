package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

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

    /**
     * 监听玩家右键收纳袋事件
     * <p>
     * 当玩家在岛屿区域内右键打开收纳袋（Bundle）时，检查是否拥有 ITEM_DROP 权限。
     * 收纳袋取出的物品若因背包满无法放入会直接掉落，需与丢弃物品权限同步约束。
     * 使用 LOWEST 优先级确保在容器权限处理前拦截。
     * </p>
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBundleInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !Tag.ITEMS_BUNDLES.isTagged(item.getType())) {
            return;
        }

        Player player = event.getPlayer();
        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.ITEM_DROP)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ITEM_DROP);
        }
    }

    /**
     * 监听掉落物生成事件，拦截收纳袋溢出物品
     * <p>
     * 当玩家在无 ITEM_DROP 权限区域右键收纳袋时，Paper 会在事件触发前
     * 自动提取物品。若背包已满，物品会以掉落物形式生成。此监听器捕获
     * 该掉落物并退还回收纳袋，防止物品丢失。
     * 通过 Item.getThrower() 获取来源玩家，精准定位。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        UUID throwerUuid = event.getEntity().getThrower();
        if (throwerUuid == null) {
            return;
        }

        Player player = Bukkit.getPlayer(throwerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        String worldName = player.getWorld().getName();
        if (!worldName.equals(configManager.getWorldNameNormal())
                && !worldName.equals(configManager.getWorldNameNether())
                && !worldName.equals(configManager.getWorldNameEnd())) {
            return;
        }

        // 检查玩家是否手持收纳袋
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!Tag.ITEMS_BUNDLES.isTagged(hand.getType())) {
            hand = player.getInventory().getItemInOffHand();
            if (!Tag.ITEMS_BUNDLES.isTagged(hand.getType())) {
                return;
            }
        }

        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.ITEM_DROP)) {
            event.setCancelled(true);
            BundleMeta meta = (BundleMeta) hand.getItemMeta();
            if (meta != null) {
                meta.addItem(event.getEntity().getItemStack().clone());
                hand.setItemMeta(meta);
            }
        }
    }

}