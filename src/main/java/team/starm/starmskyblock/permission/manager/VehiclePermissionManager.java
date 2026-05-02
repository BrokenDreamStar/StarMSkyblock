package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.tag.ItemTags;

/**
 * 载具/坐骑权限管理器
 */
public class VehiclePermissionManager extends IslandPermissionManager {

    public VehiclePermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听矿车/船破坏事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getAttacker() instanceof Player player))
            return;
        Vehicle vehicle = event.getVehicle();

        IslandPermission permission = null;
        if (vehicle instanceof Minecart)
            permission = IslandPermission.MINECART_DAMAGE;
        else if (vehicle instanceof Boat)
            permission = IslandPermission.BOAT_DAMAGE;

        if (permission != null && !checkPermission(vehicle.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 监听乘坐矿车/船事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player))
            return;

        Vehicle vehicle = event.getVehicle();
        IslandPermission permission = null;

        if (vehicle instanceof Minecart)
            permission = IslandPermission.MINECART_ENTER;
        else if (vehicle instanceof Boat)
            permission = IslandPermission.BOAT_ENTER;

        if (permission != null && !checkPermission(vehicle.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 监听玩家放置矿车/船的事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType().isAir()) {
            return;
        }

        IslandPermission permission = null;
        Block clickedBlock = event.getClickedBlock();

        if (ItemTags.MINECARTS.contains(item.getType())) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clickedBlock == null) {
                return;
            }
            if (!Tag.RAILS.isTagged(clickedBlock.getType())) {
                return;
            }
            permission = IslandPermission.MINECART_PLACE;

        } else if (Tag.ITEMS_BOATS.isTagged(item.getType())) {
            permission = IslandPermission.BOAT_PLACE;
        }

        if (permission == null) {
            return;
        }

        Location checkLoc = (clickedBlock != null)
                ? clickedBlock.getLocation()
                : player.getLocation();

        if (!checkPermission(checkLoc, player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }


}
