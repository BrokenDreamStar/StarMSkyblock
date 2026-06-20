package team.starm.starmskyblock.permission.manager;

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
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.tag.ItemTags;

/**
 * 载具权限管理器
 * <p>
 * 处理玩家与矿车和船等载具的交互权限检查。
 * 覆盖破坏、乘坐、放置三种操作，每种操作对矿车和船分别有独立的权限。
 * </p>
 */
public class VehiclePermissionManager extends BasePermissionManager {

    public VehiclePermissionManager(IslandManager islandManager, ConfigManager configManager,
                                     PublicAreaConfigManager publicAreaConfig,
                                     LockedAreaConfigManager lockedAreaConfig) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
    }

    /**
     * 监听载具被破坏事件
     * <p>
     * 过滤出玩家破坏的载具，根据类型（矿车/船）选择对应的权限。
     * 非玩家的破坏（如爆炸、环境伤害）不进行拦截。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getAttacker() instanceof Player player)) {
            return;
        }
        Vehicle vehicle = event.getVehicle();

        IslandPermission permission = switch (vehicle) {
            case Minecart m -> IslandPermission.MINECART_DAMAGE;
            case Boat b -> IslandPermission.BOAT_DAMAGE;
            default -> null;
        };

        if (permission != null) {
            enforce(event, vehicle.getLocation(), player, permission);
        }
    }

    /**
     * 监听玩家进入载具事件
     * <p>
     * 乘坐矿车和船时需要分别检查对应的进入权限。
     * 防止被拒绝进入的玩家通过载具进入他人岛屿的受限区域。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }

        Vehicle vehicle = event.getVehicle();
        IslandPermission permission = switch (vehicle) {
            case Minecart m -> IslandPermission.MINECART_ENTER;
            case Boat b -> IslandPermission.BOAT_ENTER;
            default -> null;
        };

        if (permission != null) {
            enforce(event, vehicle.getLocation(), player, permission);
        }
    }

    /**
     * 监听玩家放置载具的事件
     * <p>
     * 通过 {PlayerInteractEvent} 拦截放置矿车和船的行为：
     * <ul>
     *   <li>矿车必须右键点击铁轨方块才能放置</li>
     *   <li>船可以右键点击方块或空气放置（取决于水面等条件）</li>
     * </ul>
     * 使用 {ItemTags} 判断当前手持物品是否为矿车或船类物品。
     * </p>
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

        Location checkLoc = (clickedBlock != null) ? clickedBlock.getLocation() : player.getLocation();
        enforce(event, checkLoc, player, permission);
    }
}