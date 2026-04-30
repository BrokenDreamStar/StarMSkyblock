package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

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

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        if (isDoorMaterial(material)) {
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
     * 根据门类型获取对应的权限
     */
    private IslandPermission getDoorPermission(Material material) {
        if (Tag.TRAPDOORS.isTagged(material))
            return IslandPermission.TRAPDOOR;
        if (Tag.FENCE_GATES.isTagged(material))
            return IslandPermission.FENCE_GATE;
        return IslandPermission.DOOR;
    }

    /**
     * 检查是否可以开关门
     */
    public boolean canUseDoors(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.DOOR);
    }

    /**
     * 检查是否可以开关木门
     */
    public boolean canUseDoor(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.DOOR);
    }

    /**
     * 检查是否可以开关栅栏门
     */
    public boolean canUseFenceGate(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.FENCE_GATE);
    }

    /**
     * 检查是否可以开关活板门
     */
    public boolean canUseTrapdoor(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.TRAPDOOR);
    }
}
