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
 * 门权限管理器
 */
public class DoorPermissionManager extends BasePermissionManager {

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
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        if (isDoorMaterial(material)) {
            ItemStack item = event.getItem();

            if (item != null && isCopperDoorOrTrapdoor(material)) {
                Material itemType = item.getType();

                boolean isSneakAxe = player.isSneaking() && Tag.ITEMS_AXES.isTagged(itemType);
                boolean isSneakHoneycomb = player.isSneaking() && itemType == Material.HONEYCOMB;

                if (isSneakAxe && (material == Material.COPPER_DOOR || material == Material.COPPER_TRAPDOOR)) {
                    isSneakAxe = false;
                }
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
     * 判断是否为门
     */
    private boolean isDoorMaterial(Material material) {
        return Tag.DOORS.isTagged(material)
                || Tag.FENCE_GATES.isTagged(material)
                || Tag.TRAPDOORS.isTagged(material);
    }

    /**
     * 判断方块是否为铜门或铜活板门
     */
    private boolean isCopperDoorOrTrapdoor(Material material) {
        String name = material.name();
        return name.contains("COPPER_DOOR") || name.contains("COPPER_TRAPDOOR");
    }

    /**
     * 根据门类型获取对应的权限
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