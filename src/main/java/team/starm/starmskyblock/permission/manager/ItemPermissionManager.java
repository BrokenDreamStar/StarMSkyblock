package team.starm.starmskyblock.permission.manager;

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

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.tag.ItemTags;

/**
 * 物品权限管理器
 * 处理各类物品使用的权限检查（药水、染料、食物、投掷物等）
 */
public class ItemPermissionManager extends BasePermissionManager {

    public ItemPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家使用物品事件
     * <p>
     * 拦截玩家右键使用特定物品的操作。处理的物品类型包括：
     * <ul>
     *   <li>水瓶 -> 将泥土转为泥巴（WATER_BOTTLE_USE）</li>
     *   <li>墨囊/发光墨囊 -> 编辑告示牌（INK_SAC_USE）</li>
     *   <li>染料 -> 给告示牌/羊染色（DYE_USE）</li>
     *   <li>蜜脾 -> 给铜块/告示牌涂蜡（HONEYCOMB_USE）</li>
     *   <li>烟花/喷溅药水/滞留药水/附魔之瓶/末影珍珠等 -> 通用物品权限</li>
     * </ul>
     * 命名牌（NAME_TAG）在此处跳过检查，由 {onPlayerInteractEntity} 处理。
     * 使用 {Result.DENY} 而非简单 {setCancelled(true)} 以确保客户端完全知晓操作被拒绝。
     * </p>
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
        }
    }

    /**
     * 监听玩家右键实体使用物品事件
     * <p>
     * 处理对实体使用特定物品的权限检查：
     * <ul>
     *   <li>命名牌 -> 对生物使用时检查 NAME_TAG_USE（必须有自定义名称）</li>
     *   <li>染料 -> 对羊染色时检查 DYE_USE</li>
     * </ul>
     * 注意：染料对告示牌的使用已在 {onItemUse} 中处理。
     * </p>
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
            }
            return;
        }

        if (event.getRightClicked() instanceof Sheep sheep) {
            if (ItemTags.DYES.contains(itemType)) {
                Location loc = sheep.getLocation();
                if (!checkPermission(loc, player.getUniqueId(), IslandPermission.DYE_USE)) {
                    event.setCancelled(true);
                    sendDenyMessage(player, IslandPermission.DYE_USE);
                }
            }
        }
    }

    /**
     * 监听玩家消耗食物/物品事件
     * <p>
     * 目前仅拦截紫颂果（CHORUS_FRUIT）的食用行为。
     * 紫颂果食用后会将玩家随机传送，这涉及岛屿边界安全问题，
     * 因此需要单独的权限控制。常规食物不拦截。
     * </p>
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
            }
        }
    }

    /**
     * 监听骨粉施肥事件
     * <p>
     * {BlockFertilizeEvent} 在骨粉被使用时触发，与 {PlayerInteractEvent}
     * 不同，该事件专门处理骨粉对作物的催熟逻辑。
     * 因为骨粉的交互可能以多种方式触发（直接点击作物、通过发射器等），
     * 使用专门的事件进行拦截更可靠。
     * </p>
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
        }
    }

    /**
     * 根据物品材质映射对应的岛屿权限
     * <p>
     * 映射弹射物类物品（鸡蛋、雪球、烟花、药水、末影珍珠等）
     * 到对应的权限枚举。鸡蛋使用 {Tag.ITEMS_EGGS} 批量匹配，
     * 包含普通鸡蛋和各类衍生鸡蛋物品。
     * </p>
     *
     * @param itemMat 物品材质
     * @return 对应的岛屿权限，未知物品返回 null
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
