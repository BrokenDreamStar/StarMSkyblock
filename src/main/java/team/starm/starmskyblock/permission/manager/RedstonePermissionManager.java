package team.starm.starmskyblock.permission.manager;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.SculkSensor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;

/**
 * 红石权限管理器
 */
public class RedstonePermissionManager extends BasePermissionManager {

    public RedstonePermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与红石装置交互事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstoneInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.PHYSICAL && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK && event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Material material = block.getType();
        IslandPermission permission = getRedstonePermission(material);
        if (permission == null) {
            return;
        }

        if (permission == IslandPermission.SCULK_SENSOR_TRIGGER && action == Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        boolean isPhysicalOnlyPermission = (permission == IslandPermission.PRESSURE_PLATE_TRIGGER
                || permission == IslandPermission.TRIPWIRE_HOOK_TRIGGER
                || permission == IslandPermission.SCULK_SENSOR_TRIGGER);

        if (isPhysicalOnlyPermission && action != Action.PHYSICAL) {
            return;
        }
        if (!isPhysicalOnlyPermission && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 监听幽匿感测体触发事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSculkSensorVibration(BlockReceiveGameEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.SCULK_SENSOR && type != Material.CALIBRATED_SCULK_SENSOR) {
            return;
        }

        Entity source = event.getEntity();
        if (!(source instanceof Player player)) {
            return;
        }

        if (!checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.SCULK_SENSOR_TRIGGER)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.SCULK_SENSOR_TRIGGER);
            if (block.getBlockData() instanceof SculkSensor sensorData) {
                sensorData.setSculkSensorPhase(SculkSensor.Phase.INACTIVE);
                block.setBlockData(sensorData);
            }
        }
    }

    /**
     * 获取对应的红石元件权限
     */
    private IslandPermission getRedstonePermission(Material material) {
        if (Tag.BUTTONS.isTagged(material)) {
            return IslandPermission.BUTTON_PRESS;
        }

        if (Tag.WOODEN_PRESSURE_PLATES.isTagged(material) ||
                material == Material.STONE_PRESSURE_PLATE ||
                material == Material.POLISHED_BLACKSTONE_PRESSURE_PLATE ||
                material == Material.LIGHT_WEIGHTED_PRESSURE_PLATE ||
                material == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            return IslandPermission.PRESSURE_PLATE_TRIGGER;
        }

        return switch (material) {
            case LEVER -> IslandPermission.LEVER_USE;
            case REPEATER -> IslandPermission.REPEATER_USE;
            case COMPARATOR -> IslandPermission.COMPARATOR_USE;
            case DAYLIGHT_DETECTOR -> IslandPermission.DAYLIGHT_DETECTOR_USE;
            case BELL -> IslandPermission.BELL_RING;
            case SCULK_SENSOR, CALIBRATED_SCULK_SENSOR -> IslandPermission.SCULK_SENSOR_TRIGGER;
            case TRIPWIRE_HOOK, TRIPWIRE -> IslandPermission.TRIPWIRE_HOOK_TRIGGER;
            default -> null;
        };
    }
}