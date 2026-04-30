package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
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
        org.bukkit.entity.Vehicle vehicle = event.getVehicle();

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
     * 监听载具进入事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player))
            return;

        org.bukkit.entity.Vehicle vehicle = event.getVehicle();
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
        if (item == null) {
            return;
        }

        String typeName = item.getType().name();
        IslandPermission permission = null;

        if (typeName.endsWith("MINECART")) {
            permission = IslandPermission.MINECART_PLACE;
        } else if (typeName.endsWith("_BOAT") || typeName.endsWith("RAFT")) {
            permission = IslandPermission.BOAT_PLACE;
        }

        if (permission == null) {
            return;
        }

        // 使用点击位置作为检查坐标
        Location checkLoc = (event.getClickedBlock() != null)
                ? event.getClickedBlock().getLocation()
                : player.getLocation();

        if (!checkPermission(checkLoc, player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 检查是否可以破坏矿车
     */
    public boolean canDamageMinecart(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.MINECART_DAMAGE);
    }

    /**
     * 检查是否可以乘坐矿车
     */
    public boolean canEnterMinecart(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.MINECART_ENTER);
    }

    /**
     * 检查是否可以放置矿车
     */
    public boolean canPlaceMinecart(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.MINECART_PLACE);
    }

    /**
     * 检查是否可以破坏船
     */
    public boolean canDamageShip(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.BOAT_DAMAGE);
    }

    /**
     * 检查是否可以乘坐船
     */
    public boolean canEnterShip(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.BOAT_ENTER);
    }

    /**
     * 检查是否可以放置船
     */
    public boolean canPlaceShip(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.BOAT_PLACE);
    }
}
