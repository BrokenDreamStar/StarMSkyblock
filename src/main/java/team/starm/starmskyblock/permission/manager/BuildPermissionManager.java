package team.starm.starmskyblock.permission.manager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 方块破坏/建造权限管理器
 * <p>
 * 监听方块放置、破坏、悬挂实体（画/展示框）的放置与破坏，
 * 以及末地水晶和盔甲架的放置等与建造相关的事件。
 * 基于玩家所在岛屿的权限配置进行拦截。
 * </p>
 */
public class BuildPermissionManager extends BasePermissionManager {

    public BuildPermissionManager(IslandManager islandManager, ConfigManager configManager,
                                   PublicAreaConfigManager publicAreaConfig,
                                   LockedAreaConfigManager lockedAreaConfig) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
    }

    /**
     * 监听方块破坏事件
     * <p>
     * 在玩家破坏方块时检查 BREAK 权限。
     * 包含对所有类型方块（包括末地水晶、盔甲架等实体）的基础破坏拦截。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!checkPermission(event.getBlock().getLocation(), player.getUniqueId(), IslandPermission.BREAK)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.BREAK);
        }
    }

    /**
     * 监听方块放置事件
     * <p>
     * 在玩家放置方块时检查 BUILD 权限。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!checkPermission(event.getBlock().getLocation(), player.getUniqueId(), IslandPermission.BUILD)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.BUILD);
        }
    }

    /**
     * 监听悬挂实体（画/物品展示框）破坏事件
     * <p>
     * 悬挂实体的破坏被视为"破坏"行为，统一使用 BREAK 权限进行控制。
     * 过滤出玩家触发的破坏事件。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            if (!checkPermission(event.getEntity().getLocation(), player.getUniqueId(), IslandPermission.BREAK)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.BREAK);
            }
        }
    }

    /**
     * 监听悬挂实体（画/物品展示框）放置事件
     * <p>
     * 悬挂实体的放置被视为"建造"行为，统一使用 BUILD 权限进行控制。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && !checkPermission(event.getEntity().getLocation(), player.getUniqueId(), IslandPermission.BUILD)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.BUILD);
        }
    }

    /**
     * 监听右键交互事件（末地水晶/盔甲架放置）
     * <p>
     * 末地水晶和盔甲架通过右键点击方块放置，不是 BlockPlaceEvent。
     * 需要用 PlayerInteractEvent 额外拦截：
     * <ul>
     *   <li>末地水晶只能放在黑曜石或基岩上</li>
     *   <li>盔甲架可直接放置在任意方块上</li>
     * </ul>
     * 对这两种物品的放置行为检查 BUILD 权限。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Material itemType = event.getItem().getType();

        if (itemType == Material.ARMOR_STAND || itemType == Material.END_CRYSTAL) {
            // 末地水晶只能放在黑曜石或基岩上，否则不处理
            if (itemType == Material.END_CRYSTAL) {
                Material clickedType = event.getClickedBlock().getType();
                if (clickedType != Material.OBSIDIAN && clickedType != Material.BEDROCK) {
                    return;
                }
            }

            // 计算物品将放置的位置
            Location placeLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            if (!checkPermission(placeLoc, player.getUniqueId(), IslandPermission.BUILD)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.BUILD);
            }
        }
    }
}