package team.starm.starmskyblock.permission.manager;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 工作方块权限管理器
 * 处理工作台、附魔台、信标等工作方块的权限
 */
public class WorkblockPermissionManager extends IslandPermissionManager {

    public WorkblockPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与工作方块交互事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWorkblockInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }

        // 左键点击方块为破坏行为，由 BlockBreakEvent 处理 BREAK 权限
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        IslandPermission permission = getWorkblockPermission(material);
        if (permission != null
                && !(permission == IslandPermission.CAMPFIRE_USE && !isCookingAttempt(event))
                && !checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 获取对应的工作方块权限
     */
    private IslandPermission getWorkblockPermission(Material material) {
        return switch (material) {
            case CRAFTING_TABLE -> IslandPermission.CRAFTING_TABLE_USE;
            case ENCHANTING_TABLE -> IslandPermission.ENCHANTING_TABLE_USE;
            case BEACON -> IslandPermission.BEACON_USE;
            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> IslandPermission.ANVIL_USE;
            case GRINDSTONE -> IslandPermission.GRINDSTONE_USE;
            case CARTOGRAPHY_TABLE -> IslandPermission.CARTOGRAPHY_TABLE_USE;
            case STONECUTTER -> IslandPermission.STONECUTTER_USE;
            case LOOM -> IslandPermission.LOOM_USE;
            case SMITHING_TABLE -> IslandPermission.SMITHING_TABLE_USE;
            case CAMPFIRE, SOUL_CAMPFIRE -> IslandPermission.CAMPFIRE_USE;
            case NOTE_BLOCK -> IslandPermission.NOTE_BLOCK_USE;
            default -> null;
        };
    }

    /**
     * 判断是否尝试在营火上放置食物进行烹饪
     */
    private boolean isCookingAttempt(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }

        Material itemType = event.getItem().getType();
        return switch (itemType) {
            case BEEF, CHICKEN, PORKCHOP, MUTTON, RABBIT,
                 COD, SALMON, POTATO, KELP -> true;
            default -> false;
        };
    }

}