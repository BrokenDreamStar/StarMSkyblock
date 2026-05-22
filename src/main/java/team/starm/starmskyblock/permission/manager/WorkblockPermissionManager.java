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
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;

/**
 * 工作方块权限管理器
 * <p>
 * 处理玩家与各种功能性工作方块交互的权限检查，
 * 包括工作台、附魔台、信标、铁砧、砂轮、制图台、切石机、
 * 织布机、锻造台、营火（仅烹饪操作受控）等。
 * </p>
 */
public class WorkblockPermissionManager extends BasePermissionManager {

    public WorkblockPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与工作方块交互事件
     * <p>
     * 只处理主手右键点击方块的事件。
     * 特殊处理营火：只有当玩家尝试在营火上放置食物进行烹饪时才检查权限，
     * 普通的营火交互（如右键打开GUI）不受限制。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWorkblockInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        IslandPermission permission = getWorkblockPermission(material);
        // 营火：只有烹饪操作才检查权限，单纯交互（如熄灭/点火）不受限
        if (permission != null
                && !(permission == IslandPermission.CAMPFIRE_USE && !isCookingAttempt(event))
                && !checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 根据方块材质获取对应的工作方块权限
     * <p>
     * 将 Minecraft 方块类型映射到 {IslandPermission} 中定义的
     * 工作方块类权限。损坏的铁砧和灵魂营火也归于同一种权限。
     * </p>
     *
     * @param material 方块的材质类型
     * @return 对应的岛屿权限，非工作方块返回 null
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
            default -> null;
        };
    }

    /**
     * 判断玩家当前交互是否为在营火上放置食物进行烹饪
     * <p>
     * 生肉、生鱼、马铃薯和海带等食材可以放在营火上烹饪。
     * 只有烹饪操作才需要检查 CAMPFIRE_USE 权限，
     * 普通的营火交互（点火、用桶熄灭）不需要此权限。
     * </p>
     *
     * @param event 玩家交互事件
     * @return true 表示正在尝试烹饪
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