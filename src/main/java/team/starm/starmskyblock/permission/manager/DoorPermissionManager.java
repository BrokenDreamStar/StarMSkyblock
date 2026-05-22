// DoorPermissionManager.java
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
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;

/**
 * 门/栅栏门/活板门权限管理器
 * <p>
 * 处理玩家与各种门的交互权限检查，包括木门/铁门/铜门、
 * 栅栏门、活板门。特别注意铜门/铜活板门使用斧头脱蜡
 * 和用蜜脾涂蜡的特殊情况，这些操作不应被门权限拦截。
 * </p>
 */
public class DoorPermissionManager extends BasePermissionManager {

    public DoorPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与门交互事件
     * <p>
     * 处理右键点击门、栅栏门、活板门时的权限检查。
     * 潜行+斧头对铜门脱蜡和潜行+蜜脾对铜门上蜡属于功能性操作，
     * 不应该受门的开关权限限制，因此需要特殊排除。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDoorInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        if (isDoorMaterial(material)) {
            ItemStack item = event.getItem();

            // 铜门/铜活板门的特殊处理：潜行斧头脱蜡和潜行蜜脾涂蜡不受门权限限制
            if (item != null && isCopperDoorOrTrapdoor(material)) {
                Material itemType = item.getType();

                boolean isSneakAxe = player.isSneaking() && Tag.ITEMS_AXES.isTagged(itemType);
                boolean isSneakHoneycomb = player.isSneaking() && itemType == Material.HONEYCOMB;

                // 未涂蜡的铜门/铜活板门可以用斧头脱蜡吗？不，这不应该触发 —— 只有上了蜡的才能脱蜡
                if (isSneakAxe && (material == Material.COPPER_DOOR || material == Material.COPPER_TRAPDOOR)) {
                    isSneakAxe = false;
                }
                // 已经涂蜡的不能再涂蜡
                if (isSneakHoneycomb && material.name().contains("WAXED_")) {
                    isSneakHoneycomb = false;
                }

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
     * 判断方块是否为门/栅栏门/活板门材质
     * <p>
     * 使用 Bukkit 的 {Tag} 系统统一判断，
     * 比手动列出所有材质更简洁且兼容未来的 Minecraft 版本。
     * </p>
     *
     * @param material 方块材质
     * @return true 如果是门/栅栏门/活板门
     */
    private boolean isDoorMaterial(Material material) {
        return Tag.DOORS.isTagged(material)
                || Tag.FENCE_GATES.isTagged(material)
                || Tag.TRAPDOORS.isTagged(material);
    }

    /**
     * 判断方块是否为铜门或铜活板门
     * <p>
     * 使用材质名称字符串检查，因为 Bukkit 未提供专门的 Tag
     * 来区分铜门与其他门。名称包含 "COPPER_DOOR" 或
     * "COPPER_TRAPDOOR" 即匹配（包括涂蜡和未涂蜡变种）。
     * </p>
     *
     * @param material 方块材质
     * @return true 如果是铜门或铜活板门
     */
    private boolean isCopperDoorOrTrapdoor(Material material) {
        String name = material.name();
        return name.contains("COPPER_DOOR") || name.contains("COPPER_TRAPDOOR");
    }

    /**
     * 根据门类型映射对应的权限枚举
     * <p>
     * 门 -> DOOR_OPEN，栅栏门 -> FENCE_GATE_OPEN，活板门 -> TRAPDOOR_OPEN。
     * 三种类型的权限相互独立，方便岛屿管理员分别控制。
     * </p>
     *
     * @param material 门方块材质
     * @return 对应的岛屿权限
     */
    private IslandPermission getDoorPermission(Material material) {
        if (Tag.TRAPDOORS.isTagged(material)) {
            return IslandPermission.TRAPDOOR_OPEN;
        }
        if (Tag.FENCE_GATES.isTagged(material)) {
            return IslandPermission.FENCE_GATE_OPEN;
        }
        return IslandPermission.DOOR_OPEN;
    }
}