package team.starm.starmskyblock.permission.manager;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 门权限管理器
 * 处理门、栅栏门、活板门等开关门的权限
 */
public class DoorPermissionManager extends IslandPermissionManager {

    public DoorPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与门交互事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDoorInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }

        // 左键点击方块为破坏行为，由 BlockBreakEvent 处理 BREAK 权限
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        if (isDoorMaterial(material)) {
            ItemStack item = event.getItem();

            // 检查是否为铜门/铜活板门相关的涂蜡与除锈操作
            if (item != null && isCopperDoorOrTrapdoor(material)) {
                Material itemType = item.getType();

                // 对可交互方块进行修改，都必须处于潜行(Shift)状态
                boolean isSneakAxe = player.isSneaking() && Tag.ITEMS_AXES.isTagged(itemType);
                boolean isSneakHoneycomb = player.isSneaking() && itemType == Material.HONEYCOMB;

                // 原版中，未氧化且未涂蜡的普通铜门/活板门无法使用斧头操作
                if (isSneakAxe && (material == Material.COPPER_DOOR || material == Material.COPPER_TRAPDOOR)) {
                    isSneakAxe = false;
                }

                // 原版中，已经涂蜡的门无法再次使用蜜脾涂蜡
                if (isSneakHoneycomb && material.name().contains("WAXED_")) {
                    isSneakHoneycomb = false;
                }

                // 如果是对铜门/铜活板门进行有效的涂蜡或除锈/脱蜡操作，直接跳过门开关的权限检查
                if (isSneakAxe || isSneakHoneycomb) {
                    return;
                }
            }

            IslandPermission permission = getDoorPermission(material);

            if (!checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
                event.setCancelled(true);
                sendDenyMessage(player, permission);
            }
        }
    }

    /**
     * 使用 Bukkit Tag 判断是否为门相关方块
     */
    private boolean isDoorMaterial(Material material) {
        return Tag.DOORS.isTagged(material) ||
                Tag.FENCE_GATES.isTagged(material) ||
                Tag.TRAPDOORS.isTagged(material);
    }

    /**
     * 判断方块是否为铜门或铜活板门（涵盖所有氧化及涂蜡状态）
     */
    private boolean isCopperDoorOrTrapdoor(Material material) {
        String name = material.name();
        return name.contains("COPPER_DOOR") || name.contains("COPPER_TRAPDOOR");
    }

    /**
     * 根据门类型获取对应的权限
     */
    private IslandPermission getDoorPermission(Material material) {
        if (Tag.TRAPDOORS.isTagged(material))
            return IslandPermission.TRAPDOOR_OPEN;

        if (Tag.FENCE_GATES.isTagged(material))
            return IslandPermission.FENCE_GATE_OPEN;

        return IslandPermission.DOOR_OPEN;
    }
}
