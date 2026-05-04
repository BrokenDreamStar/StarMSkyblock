package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.ChiseledBookshelf;
import org.bukkit.block.data.type.Lectern;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.tag.EntityTags;

/**
 * 容器权限管理器
 */
public class ContainerPermissionManager extends IslandPermissionManager {

    public ContainerPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与容器交互事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        // 对铜箱子使用斧头进行脱蜡/除锈
        if (Tag.COPPER_CHESTS.isTagged(material) &&
                player.isSneaking() &&
                event.getItem() != null &&
                Tag.ITEMS_AXES.isTagged(event.getItem().getType())) {

            if (!checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.AXE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.AXE);
            }
            return;
        }

        IslandPermission permission = getContainerPermission(block, material);

        if (permission != null && !checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }

        // 唱片机 放置/取出唱片
        if (material == Material.JUKEBOX) {
            Jukebox jukebox = (Jukebox) block.getState();
            ItemStack heldItem = event.getItem();
            boolean isMusicDisc = heldItem != null && heldItem.getType().isRecord();
            ItemStack record = jukebox.getRecord();
            boolean hasRecord = record.getType() != Material.AIR;

            if ((isMusicDisc || hasRecord) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.JUKEBOX)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.JUKEBOX);
            }
        }

        // 展示架 放置/取出物品
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

            if ((hasHeldItem || hasContent) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.SHELF)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.SHELF);
            }
        }

        // 讲台 放置/取出书与笔或成书
        if (material == Material.LECTERN) {
            Lectern lectern = (Lectern) block.getBlockData();
            ItemStack heldItem = event.getItem();
            boolean holdingValidBook = heldItem != null && heldItem.getType() != Material.AIR &&
                    (heldItem.getType() == Material.WRITABLE_BOOK || heldItem.getType() == Material.WRITTEN_BOOK);
            boolean hasBook = lectern.hasBook();

            if ((holdingValidBook || hasBook) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.LECTERN)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.LECTERN);
            }
        }

        // 雕纹书架 放置/取出书与笔或成书以及附魔书
        if (material == Material.CHISELED_BOOKSHELF) {
            ChiseledBookshelf cbs = (ChiseledBookshelf) block.getBlockData();
            ItemStack heldItem = event.getItem();
            boolean holdingValidBook = heldItem != null && heldItem.getType() != Material.AIR &&
                    (heldItem.getType() == Material.WRITABLE_BOOK ||
                            heldItem.getType() == Material.WRITTEN_BOOK ||
                            heldItem.getType() == Material.ENCHANTED_BOOK);

            boolean hasBook = false;
            for (int i = 0; i < 6; i++) {
                if (cbs.isSlotOccupied(i)) {
                    hasBook = true;
                    break;
                }
            }

            if ((holdingValidBook || hasBook) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.CHISELED_BOOKSHELF)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.CHISELED_BOOKSHELF);
            }
        }

        // 堆肥桶 堆肥
        if (material == Material.COMPOSTER) {
            ItemStack heldItem = event.getItem();
            Levelled composterData = (Levelled) block.getBlockData();
            boolean isPlacing = (heldItem != null && heldItem.getType() != Material.AIR);
            boolean isTaking = (composterData.getLevel() == composterData.getMaximumLevel());

            if ((isPlacing || isTaking) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.COMPOSTER)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.COMPOSTER);
            }
        }

        // 花盆 放置/取出花 仙人掌 竹子 等
        if (Tag.FLOWER_POTS.isTagged(material)) {
            // 如果点击的不是空花盆(如 POTTED_CACTUS)，右键行为必定是取出植物
            boolean isTaking = (material != Material.FLOWER_POT);
            boolean isPlanting = false;

            // 如果点击的是空花盆，检查手中是否拿着可以种植的植物
            if (material == Material.FLOWER_POT) {
                ItemStack heldItem = event.getItem();
                if (heldItem != null && isPottable(heldItem.getType())) {
                    isPlanting = true;
                }
            }

            // 只有当玩家确实在进行放置植物或取出植物的操作时，才检查权限
            if ((isPlanting || isTaking) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.FLOWER_POT)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.FLOWER_POT);
            }
        }
    }

    /**
     * 判断物品是否可以种植在花盆中
     */
    private boolean isPottable(Material type) {
        if (type == null || type == Material.AIR) {
            return false;
        }

        // 利用 Bukkit 自带的 Tag 验证树苗和所有小型花朵
        if (Tag.SAPLINGS.isTagged(type) || Tag.SMALL_FLOWERS.isTagged(type)) {
            return true;
        }

        // 对于没有被 Tag 涵盖的其他特殊植物，单独进行枚举判断
        return switch (type) {
            case AZALEA, FLOWERING_AZALEA, MANGROVE_PROPAGULE, // 杜鹃花丛、红树胎生苗
                 DEAD_BUSH, FERN, CACTUS, BAMBOO,              // 枯萎的灌木、蕨、仙人掌、竹子
                 RED_MUSHROOM, BROWN_MUSHROOM,                 // 蘑菇
                 CRIMSON_FUNGUS, WARPED_FUNGUS,                // 下界菌
                 CRIMSON_ROOTS, WARPED_ROOTS                   // 菌索
                    -> true;
            default -> false;
        };
    }

    /**
     * 监听玩家取出展示框中的物品事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame itemFrame && event.getDamager() instanceof Player player) {
            ItemStack framedItem = itemFrame.getItem();
            boolean hasFramedItem = framedItem.getType() != Material.AIR;

            if (hasFramedItem) {
                if (!checkPermission(itemFrame.getLocation(), player.getUniqueId(), IslandPermission.ITEM_FRAME)) {
                    event.setCancelled(true);
                    sendDenyMessage(player, IslandPermission.ITEM_FRAME);
                }
            }
        }
    }

    /**
     * 监听物品展示框交互事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        if (event.getRightClicked() instanceof ItemFrame itemFrame) {
            Player player = event.getPlayer();
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            boolean hasHeldItem = heldItem.getType() != Material.AIR;

            ItemStack framedItem = itemFrame.getItem();
            boolean hasFramedItem = framedItem.getType() != Material.AIR;

            if (hasHeldItem || hasFramedItem) {
                if (!checkPermission(itemFrame.getLocation(), player.getUniqueId(), IslandPermission.ITEM_FRAME)) {
                    event.setCancelled(true);
                    sendDenyMessage(player, IslandPermission.ITEM_FRAME);
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
        if (!worldName.equals(configManager.getWorldNameNormal()) &&
                !worldName.equals(configManager.getWorldNameNether()) &&
                !worldName.equals(configManager.getWorldNameEnd())) {
            return;
        }

        Location loc = player.getLocation();
        if (!checkPermission(loc, player.getUniqueId(), IslandPermission.ENDER_CHEST)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ENDER_CHEST);
        }
    }

    /**
     * 监听打开载具容器/生物背包事件
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
                    ? IslandPermission.HOPPER
                    : IslandPermission.CHEST;
        }

        if (neededPerm == null && isAnimalWithInventory(holderEntity)) {
            neededPerm = IslandPermission.ANIMAL_INVENTORY;
        }

        if (neededPerm != null && !checkPermission(loc, player.getUniqueId(), neededPerm)) {
            event.setCancelled(true);
            sendDenyMessage(player, neededPerm);
        }
    }

    /**
     * 监听非骑乘状态下打开生物背包事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalInventoryInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity clicked = event.getRightClicked();
        if (!isAnimalWithInventory(clicked)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!checkPermission(clicked.getLocation(), player.getUniqueId(), IslandPermission.ANIMAL_INVENTORY)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ANIMAL_INVENTORY);
        }
    }

    /**
     * 根据方块类型获取对应的容器权限
     */
    private IslandPermission getContainerPermission(Block block, Material material) {
        if (block.getState() instanceof InventoryHolder) {

            // 使用 Tag 判断箱子 (涵盖木箱子、陷阱箱、以及所有种类的铜箱子)
            if (material == Material.CHEST || material == Material.TRAPPED_CHEST || Tag.COPPER_CHESTS.isTagged(material)) {
                return IslandPermission.CHEST;
            }

            // 使用 Tag 判断所有的潜影盒（涵盖原色及所有16种染色）
            if (Tag.SHULKER_BOXES.isTagged(material)) {
                return IslandPermission.SHULKER_BOX;
            }

            return switch (material) {
                case FURNACE, FURNACE_MINECART, BLAST_FURNACE, SMOKER -> IslandPermission.FURNACE;
                case BARREL -> IslandPermission.BARREL;
                case HOPPER -> IslandPermission.HOPPER;
                case DISPENSER -> IslandPermission.DISPENSER;
                case DROPPER -> IslandPermission.DROPPER;
                case CRAFTER -> IslandPermission.CRAFTING;
                case BREWING_STAND -> IslandPermission.BREWING_STAND;
                case DECORATED_POT -> IslandPermission.DECORATED_POT;
                case RESPAWN_ANCHOR -> IslandPermission.RESPAWN_ANCHOR;
                default -> null;
            };
        }
        return null;
    }

    /**
     * 判断实体是否为带容器的载具
     */
    public boolean isContainerVehicle(Entity entity) {
        EntityType type = entity.getType();
        return EntityTags.CHEST_BOATS.contains(type)
                || type == EntityType.CHEST_MINECART
                || type == EntityType.HOPPER_MINECART;
    }

    /**
     * 判断实体是否为携带背包的可骑乘生物
     */
    private boolean isAnimalWithInventory(Entity entity) {
        return entity instanceof Horse ||
                entity instanceof Donkey ||
                entity instanceof Mule ||
                entity instanceof Llama ||
                entity instanceof SkeletonHorse ||
                entity instanceof ZombieHorse ||
                entity instanceof Camel ||
                entity instanceof Nautilus ||
                entity instanceof ZombieNautilus;
    }
}
