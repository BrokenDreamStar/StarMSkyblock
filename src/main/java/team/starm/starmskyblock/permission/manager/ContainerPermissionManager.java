package team.starm.starmskyblock.permission.manager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.ChiseledBookshelf;
import org.bukkit.block.data.type.Lectern;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.tag.EntityTags;

/**
 * 容器权限管理器
 */
public class ContainerPermissionManager extends BasePermissionManager {

    public ContainerPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与容器或特殊功能方块的交互事件 (右键方块)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        // 铜箱子脱蜡/除锈
        if (Tag.COPPER_CHESTS.isTagged(material)
                && player.isSneaking()
                && event.getItem() != null
                && Tag.ITEMS_AXES.isTagged(event.getItem().getType())) {
            if (!checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.AXE_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.AXE_USE);
            }
            return;
        }

        // 常规容器交互
        IslandPermission permission = getContainerPermission(block, material);
        if (permission != null && !checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }

        // 唱片机
        if (material == Material.JUKEBOX) {
            Jukebox jukebox = (Jukebox) block.getState();
            ItemStack heldItem = event.getItem();
            boolean isMusicDisc = heldItem != null && heldItem.getType().isRecord();
            boolean hasRecord = jukebox.getRecord().getType() != Material.AIR;

            if ((isMusicDisc || hasRecord)
                    && !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.JUKEBOX_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.JUKEBOX_USE);
            }
        }

        // 展示架
        if (Tag.WOODEN_SHELVES.isTagged(block.getType())) {
            ItemStack heldItem = event.getItem();
            boolean hasHeldItem = (heldItem != null && heldItem.getType() != Material.AIR);
            boolean hasContent = false;

            if (block.getState() instanceof InventoryHolder holder) {
                for (ItemStack item : holder.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        hasContent = true;
                        break;
                    }
                }
            }

            if ((hasHeldItem || hasContent)
                    && !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.SHELF_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.SHELF_USE);
            }
        }

        // 讲台
        if (material == Material.LECTERN) {
            Lectern lectern = (Lectern) block.getBlockData();
            ItemStack heldItem = event.getItem();
            boolean holdingValidBook = heldItem != null && heldItem.getType() != Material.AIR
                    && (heldItem.getType() == Material.WRITABLE_BOOK || heldItem.getType() == Material.WRITTEN_BOOK);
            boolean hasBook = lectern.hasBook();

            if ((holdingValidBook || hasBook)
                    && !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.LECTERN_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.LECTERN_USE);
            }
        }

        // 雕纹书架
        if (material == Material.CHISELED_BOOKSHELF) {
            ChiseledBookshelf cbs = (ChiseledBookshelf) block.getBlockData();
            ItemStack heldItem = event.getItem();

            boolean holdingValidBook = heldItem != null && heldItem.getType() != Material.AIR
                    && (heldItem.getType() == Material.BOOK
                    || heldItem.getType() == Material.WRITABLE_BOOK
                    || heldItem.getType() == Material.WRITTEN_BOOK
                    || heldItem.getType() == Material.ENCHANTED_BOOK);

            boolean hasBook = false;
            boolean hasEmptySlot = false;
            for (int i = 0; i < 6; i++) {
                if (cbs.isSlotOccupied(i)) {
                    hasBook = true;
                } else {
                    hasEmptySlot = true;
                }
            }

            boolean isPlacing = holdingValidBook && hasEmptySlot;
            boolean isEmptyHand = (heldItem == null || heldItem.getType() == Material.AIR);
            boolean isTaking = isEmptyHand && hasBook;

            if ((isPlacing || isTaking)
                    && !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.CHISELED_BOOKSHELF_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.CHISELED_BOOKSHELF_USE);
            }
        }

        // 堆肥桶
        if (material == Material.COMPOSTER) {
            ItemStack heldItem = event.getItem();
            Levelled composterData = (Levelled) block.getBlockData();
            boolean isPlacing = (heldItem != null && heldItem.getType() != Material.AIR);
            boolean isTaking = (composterData.getLevel() == composterData.getMaximumLevel());

            if ((isPlacing || isTaking)
                    && !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.COMPOSTER_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.COMPOSTER_USE);
            }
        }

        // 花盆
        if (Tag.FLOWER_POTS.isTagged(material)) {
            boolean isTaking = (material != Material.FLOWER_POT);
            boolean isPlanting = false;

            if (material == Material.FLOWER_POT) {
                ItemStack heldItem = event.getItem();
                if (heldItem != null && isPottable(heldItem.getType())) {
                    isPlanting = true;
                }
            }

            if ((isPlanting || isTaking)
                    && !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.FLOWER_POT_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.FLOWER_POT_USE);
            }
        }
    }

    /**
     * 监听玩家左键攻击展示框以取出或破坏物品的事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame itemFrame && event.getDamager() instanceof Player player) {
            boolean hasFramedItem = itemFrame.getItem().getType() != Material.AIR;
            if (hasFramedItem) {
                if (!checkPermission(itemFrame.getLocation(), player.getUniqueId(), IslandPermission.ITEM_FRAME_USE)) {
                    event.setCancelled(true);
                    sendDenyMessage(player, IslandPermission.ITEM_FRAME_USE);
                }
            }
        }
    }

    /**
     * 监听玩家右键物品展示框交互事件 (放入物品或旋转物品)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getRightClicked() instanceof ItemFrame itemFrame) {
            Player player = event.getPlayer();
            ItemStack heldItem = player.getInventory().getItem(event.getHand());

            boolean hasHeldItem = heldItem.getType() != Material.AIR;
            boolean hasFramedItem = itemFrame.getItem().getType() != Material.AIR;

            if (hasHeldItem || hasFramedItem) {
                if (!checkPermission(itemFrame.getLocation(), player.getUniqueId(), IslandPermission.ITEM_FRAME_USE)) {
                    event.setCancelled(true);
                    sendDenyMessage(player, IslandPermission.ITEM_FRAME_USE);
                }
            }
        }
    }

    /**
     * 监听末影箱打开事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }

        String worldName = player.getWorld().getName();
        if (!worldName.equals(configManager.getWorldNameNormal())
                && !worldName.equals(configManager.getWorldNameNether())
                && !worldName.equals(configManager.getWorldNameEnd())) {
            return;
        }

        Location loc = player.getLocation();
        if (!checkPermission(loc, player.getUniqueId(), IslandPermission.ENDER_CHEST_OPEN)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ENDER_CHEST_OPEN);
        }
    }

    /**
     * 监听打开载具容器或生物背包事件 (例如漏斗矿车、箱子矿车等实体容器)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        Object holder = inventory.getHolder();
        if (!(holder instanceof Entity holderEntity)) {
            return;
        }

        Location loc = holderEntity.getLocation();
        IslandPermission neededPerm = null;

        if (isContainerVehicle(holderEntity)) {
            neededPerm = (holderEntity.getType() == EntityType.HOPPER_MINECART)
                    ? IslandPermission.HOPPER_OPEN
                    : IslandPermission.CHEST_OPEN;
        }

        if (neededPerm == null && EntityTags.ANIMALS_WITH_INVENTORY.contains(holderEntity.getType())) {
            neededPerm = IslandPermission.ANIMAL_INVENTORY_OPEN;
        }

        if (neededPerm != null && !checkPermission(loc, player.getUniqueId(), neededPerm)) {
            event.setCancelled(true);
            sendDenyMessage(player, neededPerm);
        }
    }

    /**
     * 监听非骑乘状态下，玩家潜行右键打开生物背包事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalInventoryInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity clicked = event.getRightClicked();
        if (!EntityTags.ANIMALS_WITH_INVENTORY.contains(clicked.getType())) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!checkPermission(clicked.getLocation(), player.getUniqueId(), IslandPermission.ANIMAL_INVENTORY_OPEN)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ANIMAL_INVENTORY_OPEN);
        }
    }

    /**
     * 根据方块类型获取对应的基础容器权限
     */
    private IslandPermission getContainerPermission(Block block, Material material) {
        if (block.getState() instanceof InventoryHolder) {
            if (material == Material.CHEST || material == Material.TRAPPED_CHEST || Tag.COPPER_CHESTS.isTagged(material)) {
                return IslandPermission.CHEST_OPEN;
            }
            if (Tag.SHULKER_BOXES.isTagged(material)) {
                return IslandPermission.SHULKER_BOX_OPEN;
            }
            return switch (material) {
                case FURNACE, FURNACE_MINECART, BLAST_FURNACE, SMOKER -> IslandPermission.FURNACE_OPEN;
                case BARREL -> IslandPermission.BARREL_OPEN;
                case HOPPER -> IslandPermission.HOPPER_OPEN;
                case DISPENSER -> IslandPermission.DISPENSER_OPEN;
                case DROPPER -> IslandPermission.DROPPER_OPEN;
                case CRAFTER -> IslandPermission.CRAFTER_OPEN;
                case BREWING_STAND -> IslandPermission.BREWING_STAND_OPEN;
                case DECORATED_POT -> IslandPermission.DECORATED_POT_USE;
                default -> null;
            };
        }
        return null;
    }

    /**
     * 判断实体是否为带容器的载具
     */
    private boolean isContainerVehicle(Entity entity) {
        EntityType type = entity.getType();
        return EntityTags.CHEST_BOATS.contains(type)
                || type == EntityType.CHEST_MINECART
                || type == EntityType.HOPPER_MINECART;
    }

    /**
     * 判断物品是否可以种植在花盆中
     */
    private boolean isPottable(Material type) {
        if (type == null || type == Material.AIR) {
            return false;
        }
        if (Tag.SAPLINGS.isTagged(type) || Tag.SMALL_FLOWERS.isTagged(type)) {
            return true;
        }
        return switch (type) {
            case AZALEA, FLOWERING_AZALEA, MANGROVE_PROPAGULE,
                 DEAD_BUSH, FERN, CACTUS, BAMBOO,
                 RED_MUSHROOM, BROWN_MUSHROOM,
                 CRIMSON_FUNGUS, WARPED_FUNGUS,
                 CRIMSON_ROOTS, WARPED_ROOTS -> true;
            default -> false;
        };
    }
}