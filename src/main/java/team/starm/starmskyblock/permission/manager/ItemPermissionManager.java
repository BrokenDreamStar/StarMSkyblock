package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 物品权限管理器
 * 处理烟花、药水、骨粉、染料等物品的使用权限
 */
public class ItemPermissionManager extends IslandPermissionManager {

    public ItemPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家使用物品事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        Location loc = (event.getClickedBlock() != null) ? event.getClickedBlock().getLocation() : player.getLocation();
        IslandPermission permission = getItemUsePermission(item.getType());

        if (permission != null && !checkPermission(loc, player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 根据玩家手中持有的物品类型，映射对应的岛屿权限
     */
    private IslandPermission getItemUsePermission(Material itemMat) {
        return switch (itemMat) {
            case FIREWORK_ROCKET -> IslandPermission.FIREWORK;
            case NAME_TAG -> IslandPermission.NAME;
            case POTION, SPLASH_POTION, LINGERING_POTION -> IslandPermission.POTION;
            case CHORUS_FRUIT -> IslandPermission.CHORUS_FRUIT;
            case ENDER_PEARL -> IslandPermission.ENDER_PEARL;
            case ENDER_EYE -> IslandPermission.ENDER_EYE;
            case WIND_CHARGE -> IslandPermission.WIND_CHARGE;
            case SNOWBALL -> IslandPermission.DROP_SNOWBALL;
            case EGG -> IslandPermission.DROP_EGG;
            case BONE_MEAL -> IslandPermission.FERTILIZE;
            default -> null;
        };
    }

    /**
     * 检查是否可以使用烟花
     */
    public boolean canUseFirework(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.FIREWORK);
    }

    /**
     * 是否拥有命名牌使用权限
     */
    public boolean canUseNameTag(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.NAME);
    }

    /**
     * 检查是否可以使用药水（喝/丢）
     */
    public boolean canUsePotion(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.POTION);
    }

    /**
     * 检查是否可以使用骨粉
     */
    public boolean canFertilize(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.FERTILIZE);
    }

    /**
     * 检查是否可以使用染料
     */
    public boolean canUseDye(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.DYE);
    }

    /**
     * 检查是否可以涂蜡（使用蜜脾）
     */
    public boolean canUseHoneycomb(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.HONEYCOMB);
    }

    /**
     * 检查是否可以食用紫颂果
     */
    public boolean canEatChorusFruit(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.CHORUS_FRUIT);
    }

    /**
     * 检查是否可以使用末影珍珠
     */
    public boolean canUseEnderPearl(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ENDER_PEARL);
    }

    /**
     * 检查是否可以使用末影之眼
     */
    public boolean canUseEnderEye(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ENDER_EYE);
    }

    /**
     * 检查是否可以使用风弹
     */
    public boolean canUseWindCharge(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.WIND_CHARGE);
    }

    /**
     * 检查是否可以丢雪球
     */
    public boolean canDropSnowball(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.DROP_SNOWBALL);
    }

    /**
     * 检查是否可以丢鸡蛋
     */
    public boolean canDropEgg(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.DROP_EGG);
    }
}