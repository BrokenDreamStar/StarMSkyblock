package team.starm.starmskyblock.permission.manager;

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
import org.bukkit.event.block.Action;
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
 * 负责处理玩家与岛屿上各类容器（箱子、特殊方块、实体背包等）交互时的权限验证
 */
public class ContainerPermissionManager extends IslandPermissionManager {

    public ContainerPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与容器或特殊功能方块的交互事件 (右键方块)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerInteract(PlayerInteractEvent event) {
        // 仅处理主手交互，防止双持导致的事件触发两次；确保点击了实体方块
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }

        // 左键点击方块为破坏行为，由 BlockBreakEvent 处理 BREAK 权限，这里直接放行
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material material = block.getType();

        // ---------------------------------------------------------
        // 1. 铜箱子特殊交互 (脱蜡/除锈)
        // ---------------------------------------------------------
        // 对铜箱子潜行并使用斧头进行脱蜡/除锈，需要 AXE (斧头使用) 权限
        if (Tag.COPPER_CHESTS.isTagged(material) &&
                player.isSneaking() &&
                event.getItem() != null &&
                Tag.ITEMS_AXES.isTagged(event.getItem().getType())) {

            if (!checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.AXE_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.AXE_USE);
            }
            return;
        }

        // ---------------------------------------------------------
        // 2. 常规容器交互 (箱子、桶、熔炉、发射器等)
        // ---------------------------------------------------------
        IslandPermission permission = getContainerPermission(block, material);
        if (permission != null && !checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }

        // ---------------------------------------------------------
        // 3. 特殊容器块：唱片机 放置/取出唱片
        // ---------------------------------------------------------
        if (material == Material.JUKEBOX) {
            Jukebox jukebox = (Jukebox) block.getState();
            ItemStack heldItem = event.getItem();

            // 判断手中是否拿着唱片
            boolean isMusicDisc = heldItem != null && heldItem.getType().isRecord();
            // 判断唱片机中是否已经有唱片 (准备取出)
            ItemStack record = jukebox.getRecord();
            boolean hasRecord = record.getType() != Material.AIR;

            // 如果是在放入唱片或取出唱片，检查权限
            if ((isMusicDisc || hasRecord) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.JUKEBOX_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.JUKEBOX_USE);
            }
        }

        // ---------------------------------------------------------
        // 4. 特殊容器块：展示架 放置/取出物品 (通常用于各类模组或特殊插件)
        // ---------------------------------------------------------
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

            // 如果手中有物品准备放入，或者展示架上有物品准备取出
            if ((hasHeldItem || hasContent) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.SHELF_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.SHELF_USE);
            }
        }

        // ---------------------------------------------------------
        // 5. 特殊容器块：讲台 放置/取出书与笔或成书
        // ---------------------------------------------------------
        if (material == Material.LECTERN) {
            Lectern lectern = (Lectern) block.getBlockData();
            ItemStack heldItem = event.getItem();

            boolean holdingValidBook = heldItem != null && heldItem.getType() != Material.AIR &&
                    (heldItem.getType() == Material.WRITABLE_BOOK || heldItem.getType() == Material.WRITTEN_BOOK);
            boolean hasBook = lectern.hasBook();

            if ((holdingValidBook || hasBook) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.LECTERN_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.LECTERN_USE);
            }
        }

        // ---------------------------------------------------------
        // 6. 特殊容器块：雕纹书架 放置/取出书籍
        // ---------------------------------------------------------
        if (material == Material.CHISELED_BOOKSHELF) {
            ChiseledBookshelf cbs = (ChiseledBookshelf) block.getBlockData();
            ItemStack heldItem = event.getItem();

            // 检查手中是否拿着合法的书 (包含普通书、书与笔、成书、附魔书)
            boolean holdingValidBook = heldItem != null && heldItem.getType() != Material.AIR &&
                    (heldItem.getType() == Material.BOOK ||
                            heldItem.getType() == Material.WRITABLE_BOOK ||
                            heldItem.getType() == Material.WRITTEN_BOOK ||
                            heldItem.getType() == Material.ENCHANTED_BOOK);

            // 遍历书架的 6 个槽位，判断是否有书(可取出)和是否有空位(可放入)
            boolean hasBook = false;
            boolean hasEmptySlot = false;
            for (int i = 0; i < 6; i++) {
                if (cbs.isSlotOccupied(i)) {
                    hasBook = true;
                } else {
                    hasEmptySlot = true;
                }
            }

            // 判定玩家具体行为：
            // [放置]：玩家手持合法的书，且书架有空位
            boolean isPlacing = holdingValidBook && hasEmptySlot;
            // [取出]：玩家主手为空手，且书架中至少有一本书
            boolean isEmptyHand = (heldItem == null || heldItem.getType() == Material.AIR);
            boolean isTaking = isEmptyHand && hasBook;

            // 只有当玩家确实在进行“放置”或“取出”行为时，才进行权限校验
            if ((isPlacing || isTaking) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.CHISELED_BOOKSHELF_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.CHISELED_BOOKSHELF_USE);
            }
        }

        // ---------------------------------------------------------
        // 7. 特殊容器块：堆肥桶 堆肥(放入)与收集(取出)
        // ---------------------------------------------------------
        if (material == Material.COMPOSTER) {
            ItemStack heldItem = event.getItem();
            Levelled composterData = (Levelled) block.getBlockData();

            boolean isPlacing = (heldItem != null && heldItem.getType() != Material.AIR);
            boolean isTaking = (composterData.getLevel() == composterData.getMaximumLevel());

            if ((isPlacing || isTaking) &&
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.COMPOSTER_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.COMPOSTER_USE);
            }
        }

        // ---------------------------------------------------------
        // 8. 特殊容器块：花盆 放置/取出植物
        // ---------------------------------------------------------
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
                    !checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.FLOWER_POT_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.FLOWER_POT_USE);
            }
        }
    }

    /**
     * 判断物品是否可以种植在花盆中
     *
     * @param type 物品材质
     * @return 是否可种植
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
     * 监听玩家左键攻击展示框以取出或破坏物品的事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame itemFrame && event.getDamager() instanceof Player player) {
            ItemStack framedItem = itemFrame.getItem();
            boolean hasFramedItem = framedItem.getType() != Material.AIR;

            // 如果展示框内有物品，破坏行为视为取出物品
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
        // 仅处理主手，防止触发两次
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getRightClicked() instanceof ItemFrame itemFrame) {
            Player player = event.getPlayer();
            ItemStack heldItem = player.getInventory().getItemInMainHand();

            boolean hasHeldItem = heldItem.getType() != Material.AIR;
            ItemStack framedItem = itemFrame.getItem();
            boolean hasFramedItem = framedItem.getType() != Material.AIR;

            // 无论手中是否有物品准备放入，或者框内已有物品准备旋转，均需验证权限
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
     * 末影箱虽然是个人背包，但在别人岛屿上通常需要管控能否打开
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // 确保打开的是末影箱界面
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }

        // 仅在插件配置的生存世界(主世界/地狱/末地)生效
        String worldName = player.getWorld().getName();
        if (!worldName.equals(configManager.getWorldNameNormal()) &&
                !worldName.equals(configManager.getWorldNameNether()) &&
                !worldName.equals(configManager.getWorldNameEnd())) {
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

        // 如果容器的持有者不是实体，则交由方块交互事件处理
        if (!(holder instanceof Entity holderEntity)) {
            return;
        }

        Location loc = holderEntity.getLocation();
        IslandPermission neededPerm = null;

        // 判断是否为载具容器 (箱子矿车、漏斗矿车、运输船)
        if (isContainerVehicle(holderEntity)) {
            neededPerm = (holderEntity.getType() == EntityType.HOPPER_MINECART)
                    ? IslandPermission.HOPPER_OPEN
                    : IslandPermission.CHEST_OPEN;
        }

        // 判断是否为带有背包的动物 (例如装备了箱子的羊驼、驴等)
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

        // 检查点击的生物是否属于带背包的动物
        if (!EntityTags.ANIMALS_WITH_INVENTORY.contains(clicked.getType())) {
            return;
        }

        Player player = event.getPlayer();

        // 原版机制：必须潜行状态下右键带箱子的动物才会打开其背包
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
     *
     * @param block    目标方块
     * @param material 目标材质
     * @return 对应的 IslandPermission 权限节点，若无对应权限则返回 null
     */
    private IslandPermission getContainerPermission(Block block, Material material) {
        if (block.getState() instanceof InventoryHolder) {
            // 使用 Tag 判断箱子 (涵盖木箱子、陷阱箱、以及所有种类的铜箱子)
            if (material == Material.CHEST || material == Material.TRAPPED_CHEST || Tag.COPPER_CHESTS.isTagged(material)) {
                return IslandPermission.CHEST_OPEN;
            }

            // 使用 Tag 判断所有的潜影盒（涵盖原色及所有16种染色）
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
     *
     * @param entity 目标实体
     * @return 是否为带容器的载具
     */
    public boolean isContainerVehicle(Entity entity) {
        EntityType type = entity.getType();
        return EntityTags.CHEST_BOATS.contains(type)
                || type == EntityType.CHEST_MINECART
                || type == EntityType.HOPPER_MINECART;
    }
}
