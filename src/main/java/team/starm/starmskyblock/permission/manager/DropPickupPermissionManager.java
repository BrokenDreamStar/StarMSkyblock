package team.starm.starmskyblock.permission.manager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.java.JavaPlugin;

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

    /** 最近被拒绝的收纳袋交互记录（玩家UUID → 时间戳），用于 ItemSpawnEvent 兜底返还 */
    private final Map<UUID, Long> deniedBundleInteractions = new HashMap<>();
    /** 插件主类实例，用于调度任务 */
    private final JavaPlugin plugin;

    public DropPickupPermissionManager(IslandManager islandManager, ConfigManager configManager, JavaPlugin plugin) {
        super(islandManager, configManager);
        this.plugin = plugin;
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
     * 有空间时仅提示，不阻止提取；背包满时阻止并返还物品。
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
            sendDenyMessage(player, IslandPermission.ITEM_DROP);

            // 背包未满时，物品可正常进入背包，不阻止交互
            if (player.getInventory().firstEmpty() == -1) {
                // 背包满时：阻止 Paper 继续处理收纳袋
                event.setUseItemInHand(Event.Result.DENY);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setCancelled(true);
                deniedBundleInteractions.put(player.getUniqueId(), System.currentTimeMillis());

                // 临时替换物品为 AIR，彻底阻止 Paper 提取
                EquipmentSlot hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;
                ItemStack savedBundle = hand == EquipmentSlot.HAND
                        ? player.getInventory().getItemInMainHand().clone()
                        : player.getInventory().getItemInOffHand().clone();
                if (hand == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                } else {
                    player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                }
                final EquipmentSlot finalHand = hand;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (finalHand == EquipmentSlot.HAND) {
                        player.getInventory().setItemInMainHand(savedBundle);
                    } else {
                        player.getInventory().setItemInOffHand(savedBundle);
                    }
                    player.updateInventory();
                });
            }
        }
    }

    /**
     * HIGHEST 优先级重新断言，仅背包满时阻止
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBundleInteractReenforce(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !Tag.ITEMS_BUNDLES.isTagged(item.getType())) {
            return;
        }

        Player player = event.getPlayer();
        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.ITEM_DROP)
                && player.getInventory().firstEmpty() == -1) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    /**
     * 监听掉落物生成事件，拦截收纳袋溢出的丢失物品
     * <p>
     * 当玩家在无 ITEM_DROP 权限区域右键收纳袋时，Paper 在 PlayerInteractEvent
     * 触发前就已提取物品。若背包满，提取的物品会以掉落物形式生成。
     * 此监听器通过在 onBundleInteract 中记录的拒绝记录，匹配并拦截这些掉落物，
     * 使用 BundleMeta API 将物品返还回收纳袋。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (deniedBundleInteractions.isEmpty()) {
            return;
        }

        String worldName = event.getLocation().getWorld().getName();
        if (!worldName.equals(configManager.getWorldNameNormal())
                && !worldName.equals(configManager.getWorldNameNether())
                && !worldName.equals(configManager.getWorldNameEnd())) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iter = deniedBundleInteractions.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<UUID, Long> entry = iter.next();

            // 清除超过 2 秒的过期记录
            if (now - entry.getValue() > 2000) {
                iter.remove();
                continue;
            }

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()
                    || !player.getWorld().equals(event.getLocation().getWorld())
                    || player.getLocation().distanceSquared(event.getLocation()) >= 9) {
                continue;
            }

            // 检查玩家是否手持收纳袋（主手或副手）
            boolean isMainHand = Tag.ITEMS_BUNDLES.isTagged(player.getInventory().getItemInMainHand().getType());
            boolean isOffHand = Tag.ITEMS_BUNDLES.isTagged(player.getInventory().getItemInOffHand().getType());
            if (!isMainHand && !isOffHand) {
                iter.remove();
                continue;
            }

            // 二次权限检查，确认玩家当前仍在无权限区域
            if (!checkPermission(event.getLocation(), player.getUniqueId(), IslandPermission.ITEM_DROP)) {
                event.setCancelled(true);

                ItemStack hand = isMainHand ? player.getInventory().getItemInMainHand()
                                            : player.getInventory().getItemInOffHand();
                BundleMeta meta = (BundleMeta) hand.getItemMeta();
                if (meta != null) {
                    meta.addItem(event.getEntity().getItemStack().clone());
                    hand.setItemMeta(meta);
                    // 确保 ItemStack 修改持久化到玩家背包
                    if (isMainHand) {
                        player.getInventory().setItemInMainHand(hand);
                    } else {
                        player.getInventory().setItemInOffHand(hand);
                    }
                }
            }

            iter.remove();
            break;
        }
    }

}