package team.starm.starmskyblock.permission.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.tag.ItemTags;

/**
 * 物品权限管理器
 * 处理各类物品使用的权限检查（药水、染料、食物、投掷物等）
 */
public class ItemPermissionManager extends IslandPermissionManager {

    public ItemPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家使用物品事件 (拦截投掷喷溅/滞留药水和附魔之瓶、丢雪球、蜜脾、染料等)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (item.getType() == Material.NAME_TAG) {
            return;
        }

        // 水瓶右键泥土转换
        if (item.getType() == Material.POTION && event.getClickedBlock() != null
                && Tag.CONVERTABLE_TO_MUD.isTagged(event.getClickedBlock().getType())) {
            if (item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potionMeta) {
                if (potionMeta.getBasePotionType() == org.bukkit.potion.PotionType.WATER) {
                    Location loc = event.getClickedBlock().getLocation();
                    if (!checkPermission(loc, player.getUniqueId(), IslandPermission.WATER_BOTTLE_USE)) {
                        event.setCancelled(true);
                        event.setUseItemInHand(Result.DENY);
                        event.setUseInteractedBlock(Result.DENY);
                        sendDenyMessage(player, IslandPermission.WATER_BOTTLE_USE);
                        Bukkit.getScheduler().runTask(
                                JavaPlugin.getProvidingPlugin(getClass()),
                                player::updateInventory
                        );
                    }
                    return;
                }
            }
        }

        // 墨囊/染料右键告示牌
        if (event.getClickedBlock() != null && Tag.ALL_SIGNS.isTagged(event.getClickedBlock().getType())) {
            Material type = item.getType();
            boolean isInk = (type == Material.INK_SAC || type == Material.GLOW_INK_SAC);
            boolean isDye = ItemTags.DYES.contains(type);

            if (isInk || isDye) {
                Location loc = event.getClickedBlock().getLocation();
                IslandPermission targetPerm = isInk ? IslandPermission.INK_SAC_USE : IslandPermission.DYE_USE;
                if (!checkPermission(loc, player.getUniqueId(), targetPerm)) {
                    event.setCancelled(true);
                    event.setUseItemInHand(Result.DENY);
                    event.setUseInteractedBlock(Result.DENY);
                    sendDenyMessage(player, targetPerm);
                    Bukkit.getScheduler().runTask(
                            JavaPlugin.getProvidingPlugin(getClass()),
                            player::updateInventory
                    );
                }
                return;
            }
        }

        // 蜜脾涂蜡
        if (item.getType() == Material.HONEYCOMB && event.getClickedBlock() != null) {
            Material clickedType = event.getClickedBlock().getType();
            if (ItemTags.WAXABLE_BLOCKS.contains(clickedType) || Tag.ALL_SIGNS.isTagged(clickedType)) {
                boolean isDoorOrTrapdoor = Tag.DOORS.isTagged(clickedType) || Tag.TRAPDOORS.isTagged(clickedType);
                if (!isDoorOrTrapdoor || player.isSneaking()) {
                    Location loc = event.getClickedBlock().getLocation();
                    if (!checkPermission(loc, player.getUniqueId(), IslandPermission.HONEYCOMB_USE)) {
                        event.setCancelled(true);
                        event.setUseItemInHand(Result.DENY);
                        event.setUseInteractedBlock(Result.DENY);
                        sendDenyMessage(player, IslandPermission.HONEYCOMB_USE);
                        Bukkit.getScheduler().runTask(
                                JavaPlugin.getProvidingPlugin(getClass()),
                                player::updateInventory
                        );
                    }
                    return;
                }
            }
        }

        Location loc = (event.getClickedBlock() != null) ? event.getClickedBlock().getLocation() : player.getLocation();
        IslandPermission permission = getItemUsePermission(item.getType());

        if (permission != null && !checkPermission(loc, player.getUniqueId(), permission)) {
            event.setCancelled(true);
            event.setUseItemInHand(Result.DENY);
            event.setUseInteractedBlock(Result.DENY);
            sendDenyMessage(player, permission);
            Bukkit.getScheduler().runTask(
                    JavaPlugin.getProvidingPlugin(getClass()),
                    player::updateInventory
            );
        }
    }

    /**
     * 监听玩家与实体交互事件 (拦截命名牌、染料等特定的实体交互)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType() == Material.AIR) {
            return;
        }

        Material itemType = item.getType();
        if (itemType == Material.NAME_TAG && event.getRightClicked() instanceof Mob) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) {
                return;
            }
            Location loc = event.getRightClicked().getLocation();
            if (!checkPermission(loc, player.getUniqueId(), IslandPermission.NAME_TAG_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.NAME_TAG_USE);
                player.updateInventory();
            }
            return;
        }

        if (event.getRightClicked() instanceof Sheep sheep) {
            if (ItemTags.DYES.contains(itemType)) {
                Location loc = sheep.getLocation();
                if (!checkPermission(loc, player.getUniqueId(), IslandPermission.DYE_USE)) {
                    event.setCancelled(true);
                    sendDenyMessage(player, IslandPermission.DYE_USE);
                    player.updateInventory();
                }
            }
        }
    }

    /**
     * 监听玩家消耗物品事件 (目前仅处理紫颂果食用)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item.getType() == Material.AIR) {
            return;
        }
        if (item.getType() == Material.CHORUS_FRUIT) {
            Location loc = player.getLocation();
            if (!checkPermission(loc, player.getUniqueId(), IslandPermission.CHORUS_FRUIT_EAT)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.CHORUS_FRUIT_EAT);
                Bukkit.getScheduler().runTask(
                        JavaPlugin.getProvidingPlugin(getClass()),
                        player::updateInventory
                );
            }
        }
    }

    /**
     * 监听骨粉施肥事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Location loc = event.getBlock().getLocation();
        if (!checkPermission(loc, player.getUniqueId(), IslandPermission.BONE_MEAL_USE)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.BONE_MEAL_USE);
            Bukkit.getScheduler().runTask(
                    JavaPlugin.getProvidingPlugin(getClass()),
                    player::updateInventory
            );
        }
    }

    /**
     * 根据玩家手中持有的物品类型，映射对应的岛屿权限
     */
    private IslandPermission getItemUsePermission(Material itemMat) {
        if (Tag.ITEMS_EGGS.isTagged(itemMat)) {
            return IslandPermission.EGG_THROW;
        }
        return switch (itemMat) {
            case FIREWORK_ROCKET -> IslandPermission.FIREWORK_USE;
            case SPLASH_POTION, LINGERING_POTION, EXPERIENCE_BOTTLE -> IslandPermission.POTION_THROW;
            case CHORUS_FRUIT -> IslandPermission.CHORUS_FRUIT_EAT;
            case ENDER_PEARL -> IslandPermission.ENDER_PEARL_USE;
            case ENDER_EYE -> IslandPermission.ENDER_EYE_USE;
            case WIND_CHARGE -> IslandPermission.WIND_CHARGE_USE;
            case SNOWBALL -> IslandPermission.SNOWBALL_THROW;
            default -> null;
        };
    }
}