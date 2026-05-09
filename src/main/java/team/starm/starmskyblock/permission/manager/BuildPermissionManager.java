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
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 方块权限管理器
 */
public class BuildPermissionManager extends BasePermissionManager {

    public BuildPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听破坏事件
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
     * 监听放置事件
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
     * 监听 画/展示框 破坏事件
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
     * 监听 画/展示框 放置事件
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
     * 监听 末地水晶/盔甲架 放置事件
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
            if (itemType == Material.END_CRYSTAL) {
                Material clickedType = event.getClickedBlock().getType();
                if (clickedType != Material.OBSIDIAN && clickedType != Material.BEDROCK) {
                    return;
                }
            }

            Location placeLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
            if (!checkPermission(placeLoc, player.getUniqueId(), IslandPermission.BUILD)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.BUILD);
            }
        }
    }
}