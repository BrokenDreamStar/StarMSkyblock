package team.starm.starmskyblock.permission.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
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
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 红石权限管理器
 */
public class RedstonePermissionManager extends IslandPermissionManager {

    // 防抖相关
    private static final long DENY_MESSAGE_COOLDOWN_MS = 1000L;
    private static final long CONTINUOUS_TRIGGER_THRESHOLD_MS = 500L;
    private final Map<UUID, Long> lastDenyTime = new HashMap<>();
    private final Map<UUID, Long> lastPhysicalTriggerTime = new HashMap<>();
    private final Map<UUID, Location> lastPhysicalBlock = new HashMap<>();

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

        boolean isPhysicalOnlyPermission = (permission == IslandPermission.PRESSURE_PLATE_TRIGGER ||
                permission == IslandPermission.TRIPWIRE_HOOK_TRIGGER ||
                permission == IslandPermission.SCULK_SENSOR_TRIGGER);

        if (isPhysicalOnlyPermission && action != Action.PHYSICAL) {
            return;
        }
        if (!isPhysicalOnlyPermission && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);

            // 修复：传入当前交互的 block 供防抖判断使用
            if (shouldSendDenyMessage(player, permission, block)) {
                sendDenyMessage(player, permission);
            }
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

            // 修复：传入当前的 block 供防抖判断使用
            if (shouldSendDenyMessage(player, IslandPermission.SCULK_SENSOR_TRIGGER, block)) {
                sendDenyMessage(player, IslandPermission.SCULK_SENSOR_TRIGGER);
            }

            if (block.getBlockData() instanceof SculkSensor sensorData) {
                sensorData.setSculkSensorPhase(SculkSensor.Phase.INACTIVE);
                block.setBlockData(sensorData);
            }
        }
    }

    /**
     * 防止提示信息刷屏
     */
    private boolean shouldSendDenyMessage(Player player, IslandPermission permission, Block block) {
        if (permission != IslandPermission.PRESSURE_PLATE_TRIGGER && permission != IslandPermission.TRIPWIRE_HOOK_TRIGGER && permission != IslandPermission.SCULK_SENSOR_TRIGGER) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        long lastTrigger = lastPhysicalTriggerTime.getOrDefault(uuid, 0L);
        Location lastBlock = lastPhysicalBlock.get(uuid);

        lastPhysicalTriggerTime.put(uuid, now);
        lastPhysicalBlock.put(uuid, block.getLocation());


        if (block.getLocation().equals(lastBlock) && (now - lastTrigger) < CONTINUOUS_TRIGGER_THRESHOLD_MS) {
            return false;
        }

        Long lastTime = lastDenyTime.getOrDefault(uuid, 0L);
        if (now - lastTime >= DENY_MESSAGE_COOLDOWN_MS) {
            lastDenyTime.put(uuid, now);
            return true;
        }
        return false;
    }

    /**
     * 获取对应的红石元件权限
     */
    private IslandPermission getRedstonePermission(Material material) {
        String matName = material.name();

        if (matName.endsWith("_BUTTON")) {
            return IslandPermission.BUTTON_PRESS;
        }

        if (matName.endsWith("_PRESSURE_PLATE")) {
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
