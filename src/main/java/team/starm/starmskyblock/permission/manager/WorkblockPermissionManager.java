package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
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

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        IslandPermission permission = getWorkblockPermission(material);
        if (permission != null
                && !(permission == IslandPermission.CAMPFIRE && !isCookingAttempt(event))
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
            case CRAFTING_TABLE -> IslandPermission.WORKBENCH;
            case ENCHANTING_TABLE -> IslandPermission.ENCHANTING_TABLES;
            case BEACON -> IslandPermission.BEACON;
            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> IslandPermission.ANVIL;
            case GRINDSTONE -> IslandPermission.GRINDSTONE;
            case CARTOGRAPHY_TABLE -> IslandPermission.CARTOGRAPHY_TABLE;
            case STONECUTTER -> IslandPermission.STONECUTTER;
            case LOOM -> IslandPermission.LOOM;
            case SMITHING_TABLE -> IslandPermission.SMITHING_TABLE;
            case CAMPFIRE, SOUL_CAMPFIRE -> IslandPermission.CAMPFIRE;
            case NOTE_BLOCK -> IslandPermission.NOTE_BLOCK;
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

    /**
     * 检查是否可以使用工作台
     */
    public boolean canUseWorkbench(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.WORKBENCH);
    }

    /**
     * 检查是否可以使用附魔台
     */
    public boolean canUseEnchantingTable(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ENCHANTING_TABLES);
    }

    /**
     * 检查是否可以使用信标
     */
    public boolean canUseBeacon(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.BEACON);
    }

    /**
     * 检查是否可以使用铁砧
     */
    public boolean canUseAnvil(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ANVIL);
    }

    /**
     * 检查是否可以使用砂轮
     */
    public boolean canUseGrindstone(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.GRINDSTONE);
    }

    /**
     * 检查是否可以使用制图台
     */
    public boolean canUseCartographyTable(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.CARTOGRAPHY_TABLE);
    }

    /**
     * 检查是否可以使用切石机
     */
    public boolean canUseStonecutter(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.STONECUTTER);
    }

    /**
     * 检查是否可以使用织布机
     */
    public boolean canUseLoom(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.LOOM);
    }

    /**
     * 检查是否可以使用锻造台
     */
    public boolean canUseSmithingTable(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.SMITHING_TABLE);
    }

    /**
     * 检查是否可以使用营火
     */
    public boolean canUseCampfire(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.CAMPFIRE);
    }

    /**
     * 检查是否可以使用音符盒
     */
    public boolean canUseNoteBlock(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.NOTE_BLOCK);
    }
}