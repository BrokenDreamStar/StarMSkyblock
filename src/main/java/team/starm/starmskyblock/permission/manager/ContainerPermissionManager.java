package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.type.ChiseledBookshelf;
import org.bukkit.block.data.type.Lectern;
import org.bukkit.entity.*;
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
        if (material.name().endsWith("COPPER_CHEST") &&
                player.isSneaking() &&
                event.getItem() != null &&
                event.getItem().getType().name().endsWith("_AXE")) {

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
            boolean isMusicDisc = heldItem != null && heldItem.getType().name().startsWith("MUSIC_DISC_");

            ItemStack record = jukebox.getRecord();
            boolean hasRecord = record.getType() != Material.AIR;

            if ((isMusicDisc || hasRecord) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.JUKEBOX)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.JUKEBOX);
            }
        }

        // 展示架 放置/取出物品
        if (material.name().endsWith("_SHELF")) {
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

            org.bukkit.block.data.Levelled composterData = (org.bukkit.block.data.Levelled) block.getBlockData();

            boolean isPlacing = (heldItem != null && heldItem.getType() != Material.AIR);
            boolean isTaking = (composterData.getLevel() == composterData.getMaximumLevel());

            if ((isPlacing || isTaking) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.COMPOSTER)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.COMPOSTER);
            }
        }

        // 花盆 放置/取出花 仙人掌 竹子
        if (material == Material.FLOWER_POT || material.name().startsWith("POTTED_")) {
            ItemStack heldItem = event.getItem();
            boolean isPlacing = heldItem != null && heldItem.getType() != Material.AIR;
            boolean isPotted = material.name().startsWith("POTTED_");

            boolean isRemoving = !isPlacing && isPotted;

            if ((isPlacing || isRemoving) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.FLOWER_POT)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.FLOWER_POT);
            }
        }
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

        if (event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
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
        if (block.getState() instanceof org.bukkit.inventory.InventoryHolder) {
            return switch (material) {
                case FURNACE, FURNACE_MINECART, BLAST_FURNACE, SMOKER -> IslandPermission.FURNACE;
                case CHEST, TRAPPED_CHEST, COPPER_CHEST, EXPOSED_COPPER_CHEST,
                     WEATHERED_COPPER_CHEST, OXIDIZED_COPPER_CHEST, WAXED_COPPER_CHEST,
                     WAXED_EXPOSED_COPPER_CHEST, WAXED_WEATHERED_COPPER_CHEST, WAXED_OXIDIZED_COPPER_CHEST ->
                        IslandPermission.CHEST;
                case BARREL -> IslandPermission.BARREL;
                case HOPPER -> IslandPermission.HOPPER;
                case DISPENSER -> IslandPermission.DISPENSER;
                case DROPPER -> IslandPermission.DROPPER;
                case CRAFTER -> IslandPermission.CRAFTING;
                case BREWING_STAND -> IslandPermission.BREWING_STAND;
                case SHULKER_BOX -> IslandPermission.SHULKER_BOX;
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
        String name = entity.getType().name();
        return name.endsWith("CHEST_BOAT") ||
                name.endsWith("CHEST_RAFT") ||
                name.equals("CHEST_MINECART") ||
                name.equals("HOPPER_MINECART");
    }

    /**
     * 判断实体是否为携带背包的可骑乘生物
     */
    private boolean isAnimalWithInventory(Entity entity) {
        return entity instanceof Horse ||
                entity instanceof Donkey ||
                entity instanceof Mule ||
                entity instanceof Llama ||
                entity instanceof TraderLlama ||
                entity instanceof SkeletonHorse ||
                entity instanceof ZombieHorse ||
                entity instanceof Camel ||
                entity instanceof CamelHusk ||
                entity instanceof Nautilus ||
                entity instanceof ZombieNautilus;
    }

    /**
     * 检查是否可以使用熔炉
     */
    public boolean canUseFurnace(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.FURNACE);
    }

    /**
     * 检查是否可以打开箱子
     */
    public boolean canOpenChest(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.CHEST);
    }

    /**
     * 检查是否可以打开木桶
     */
    public boolean canOpenBarrel(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.BARREL);
    }

    /**
     * 检查是否可以打开末影箱
     */
    public boolean canOpenEnderChest(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ENDER_CHEST);
    }

    /**
     * 检查是否可以打开潜影盒
     */
    public boolean canOpenShulkerBox(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.SHULKER_BOX);
    }

    /**
     * 检查是否可以使用漏斗
     */
    public boolean canUseHopper(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.HOPPER);
    }

    /**
     * 检查是否可以使用发射器
     */
    public boolean canUseDispenser(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.DISPENSER);
    }

    /**
     * 检查是否可以使用投掷器
     */
    public boolean canUseDropper(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.DROPPER);
    }

    /**
     * 检查是否可以使用酿造台
     */
    public boolean canUseBrewingStand(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.BREWING_STAND);
    }

    /**
     * 检查是否可以使用唱片机
     */
    public boolean canUseJukebox(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.JUKEBOX);
    }

    /**
     * 检查是否可以使用展示架
     */
    public boolean canUseShelf(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.SHELF);
    }

    /**
     * 检查是否可以使用讲台
     */
    public boolean canUseLectern(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.LECTERN);
    }

    /**
     * 检查是否可以使用雕纹书架
     */
    public boolean canUseChiseledBookshelf(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.CHISELED_BOOKSHELF);
    }

    /**
     * 检查是否可以使用堆肥桶
     */
    public boolean canUseComposter(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.COMPOSTER);
    }

    /**
     * 检查是否可以使用花盆
     */
    public boolean canUseFlowerPot(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.FLOWER_POT);
    }

    /**
     * 检查是否可以打开生物背包
     */
    public boolean canOpenAnimalInventory(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ANIMAL_INVENTORY);
    }
}
