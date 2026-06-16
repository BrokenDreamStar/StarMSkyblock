package team.starm.starmskyblock.permission.manager;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;

/**
 * 红石权限管理器
 * <p>
 * 处理玩家与各种红石元件的交互权限检查，包括：
 * 按钮、拉杆、压力板、绊线钩、红石中继器/比较器、
 * 阳光探测器、钟、音符盒以及幽匿感测体等。
 * </p>
 */
public class RedstonePermissionManager extends BasePermissionManager {

    public RedstonePermissionManager(IslandManager islandManager, ConfigManager configManager,
                                      PublicAreaConfigManager publicAreaConfig,
                                      LockedAreaConfigManager lockedAreaConfig) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
    }

    /**
     * 监听玩家与红石装置交互事件
     * <p>
     * 红石交互分为两种类型：
     * <ul>
     *   <li>物理交互（{Action.PHYSICAL}）：踩踏压力板、绊线等，不需要手持物品</li>
     *   <li>右键交互（{Action.RIGHT_CLICK_BLOCK}）：点击按钮、拉杆等，需要主手</li>
     * </ul>
     * 幽匿感测体比较特殊：右键点击不会触发（那是调试模式），只有物理振动才检查权限。
     * 压力板和绊线钩只能通过物理交互触发，其他红石元件只能通过右键触发。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstoneInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        // 只处理物理交互和右键方块两种操作
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

        // 幽匿感测体右键是调试模式，不应拦截
        if (permission == IslandPermission.SCULK_SENSOR_TRIGGER && action == Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 区分"仅物理"和"仅右键"的红石元件
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
     * 监听幽匿感测体振动事件
     * <p>
     * {BlockReceiveGameEvent} 在幽匿感测体接收到振动时触发。
     * 这是对幽匿感测体权限的额外拦截层，因为某些振动源（如下落的物品）
     * 可能不会经过 {PlayerInteractEvent}。取消事件后还需强制更新
     * 方块状态以重置客户端视觉效果。
     * </p>
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
            block.setBlockData(block.getBlockData(), true);
        }
    }

    /**
     * 根据材质映射对应的红石权限
     * <p>
     * 使用 {Tag} 系统批量匹配按钮类和木质压力板类方块，
     * 然后通过 switch 表达式逐一映射其他类型的红石元件。
     * </p>
     *
     * @param material 方块的材质
     * @return 对应的岛屿权限，非红石元件返回 null
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
            case NOTE_BLOCK -> IslandPermission.NOTE_BLOCK_USE;
            default -> null;
        };
    }
}