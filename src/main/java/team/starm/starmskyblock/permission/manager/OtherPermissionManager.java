package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
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
 * 其它权限管理器
 * 处理踩踏耕地、采摘浆果、使用床等杂项权限
 */
public class OtherPermissionManager extends IslandPermissionManager {

    public OtherPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与其它交互事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOtherInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        IslandPermission permission = getOtherPermission(material);
        if (permission != null && !checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 根据方块类型获取对应的其它权限
     */
    private IslandPermission getOtherPermission(Material material) {
        return switch (material) {
            case FARMLAND -> IslandPermission.FARM_TRAMPING;
            case TURTLE_EGG -> IslandPermission.TURTLE_EGG_TRAMPING;
            case SWEET_BERRY_BUSH, GLOW_BERRIES -> IslandPermission.BERRY;
            case CAKE -> IslandPermission.CAKE;
            case OAK_SIGN, SPRUCE_SIGN, BIRCH_SIGN, JUNGLE_SIGN, ACACIA_SIGN, DARK_OAK_SIGN,
                    MANGROVE_SIGN, CHERRY_SIGN, BAMBOO_SIGN, CRIMSON_SIGN, WARPED_SIGN,
                    OAK_HANGING_SIGN, SPRUCE_HANGING_SIGN, BIRCH_HANGING_SIGN, JUNGLE_HANGING_SIGN,
                    ACACIA_HANGING_SIGN, DARK_OAK_HANGING_SIGN, MANGROVE_HANGING_SIGN,
                    CHERRY_HANGING_SIGN, BAMBOO_HANGING_SIGN, CRIMSON_HANGING_SIGN, WARPED_HANGING_SIGN ->
                IslandPermission.SIGN_INTERACT;
            case RED_BED, BLACK_BED, BLUE_BED, BROWN_BED, CYAN_BED, GRAY_BED, GREEN_BED, LIGHT_BLUE_BED,
                    LIGHT_GRAY_BED, LIME_BED, MAGENTA_BED, ORANGE_BED, PINK_BED, PURPLE_BED, WHITE_BED, YELLOW_BED ->
                IslandPermission.BED;
            case RESPAWN_ANCHOR -> IslandPermission.RESPAWN_ANCHOR;
            case END_CRYSTAL -> IslandPermission.END_CRYSTAL;
            default -> null;
        };
    }

}