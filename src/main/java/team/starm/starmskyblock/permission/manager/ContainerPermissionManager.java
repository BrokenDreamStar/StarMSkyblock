package team.starm.starmskyblock.permission.manager;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
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
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.permission.PermissionCheckResult;
import team.starm.starmskyblock.tag.EntityTags;

/**
 * 容器权限管理器
 * <p>
 * 处理玩家与各种容器及功能性方块交互的权限检查。涵盖以下类型：
 * <ul>
 *   <li>常规容器：箱子、陷阱箱、铜箱、熔炉、桶、漏斗、发射器、投掷器等</li>
 *   <li>特殊容器：潜影盒、末影箱、酿造台、自动合成器、雕纹陶罐</li>
 *   <li>交互容器：唱片机、展示架、讲台、雕纹书架、堆肥桶、花盆</li>
 *   <li>实体容器：漏斗矿车、箱子矿车、箱子船、生物背包</li>
 * </ul>
 * </p>
 */
public class ContainerPermissionManager extends BasePermissionManager {

    public ContainerPermissionManager(IslandManager islandManager, ConfigManager configManager,
                                       PublicAreaConfigManager publicAreaConfig,
                                       LockedAreaConfigManager lockedAreaConfig,
                                       JavaPlugin plugin, SkyblockWorldManager worldManager) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
    }

    /**
     * 监听玩家与容器或特殊功能方块的交互事件
     * <p>
     * 处理右键点击方块时的权限检查。同一个事件处理方法中按顺序检查多种容器类型。
     * 由于 Bukkit 事件系统的限制，特殊情况的容器（如铜箱脱蜡、唱片机放/取唱片等）
     * 需要在常规容器检查之外额外判断，因为它们在 Minecraft 中的交互方式不同。
     * <p>
     * 处理顺序：
     * <ol>
     *   <li>铜箱子的脱蜡/除锈（使用斧头潜行右键）</li>
     *   <li>常规容器（InventoryHolder）</li>
     *   <li>唱片机（放/取唱片时检查）</li>
     *   <li>展示架（放/取物品时检查）</li>
     *   <li>讲台（放/取书时检查）</li>
     *   <li>雕纹书架（放/取书时检查）</li>
     *   <li>堆肥桶（放/取堆肥时检查）</li>
     *   <li>花盆（种/取植物时检查）</li>
     * </ol>
     * </p>
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

        // 铜箱子脱蜡/除锈：玩家潜行手持斧头右键铜箱子时使用 AXE_USE 权限
        if (Tag.COPPER_CHESTS.isTagged(material)
                && player.isSneaking()
                && event.getItem() != null
                && Tag.ITEMS_AXES.isTagged(event.getItem().getType())) {
            enforce(event, block.getLocation(), player, IslandPermission.AXE_USE);
            return;
        }

        // 一次解析 Location→Player/Island 状态，后续 7+ 次权限判定复用同一 result
        PermissionCheckResult r = resolve(block.getLocation(), player);

        // 常规容器交互（实现了 InventoryHolder 接口的方块）
        IslandPermission permission = getContainerPermission(block, material);
        if (permission != null) {
            enforce(event, r, player, permission);
        }

        // 唱片机：放入唱片或取出唱片时需要检查 JUKEBOX_USE 权限
        // 空手右键唱片机不会触发此检查
        if (material == Material.JUKEBOX) {
            Jukebox jukebox = (Jukebox) block.getState();
            ItemStack heldItem = event.getItem();
            boolean isMusicDisc = heldItem != null && heldItem.getType().isRecord();
            boolean hasRecord = jukebox.getRecord().getType() != Material.AIR;

            if (isMusicDisc || hasRecord) {
                enforce(event, r, player, IslandPermission.JUKEBOX_USE);
            }
        }

        // 展示架：手持物品时放入、空手且有内容时取出
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

            if (hasHeldItem || hasContent) {
                enforce(event, r, player, IslandPermission.SHELF_USE);
            }
        }

        // 讲台：放书或取书时检查 LECTERN_USE 权限
        if (material == Material.LECTERN) {
            Lectern lectern = (Lectern) block.getBlockData();
            ItemStack heldItem = event.getItem();
            boolean holdingValidBook = heldItem != null && heldItem.getType() != Material.AIR
                    && (heldItem.getType() == Material.WRITABLE_BOOK || heldItem.getType() == Material.WRITTEN_BOOK);
            boolean hasBook = lectern.hasBook();

            if (holdingValidBook || hasBook) {
                enforce(event, r, player, IslandPermission.LECTERN_USE);
            }
        }

        // 雕纹书架：放入或取出书本时检查 CHISELED_BOOKSHELF_USE 权限
        // 遍历 6 个槽位判断是否有书和是否有空位
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

            if (isPlacing || isTaking) {
                enforce(event, r, player, IslandPermission.CHISELED_BOOKSHELF_USE);
            }
        }

        // 堆肥桶：放入物品或取出骨粉时检查 COMPOSTER_USE 权限
        // 满级时才能取出，未满时放入
        if (material == Material.COMPOSTER) {
            ItemStack heldItem = event.getItem();
            Levelled composterData = (Levelled) block.getBlockData();
            boolean isPlacing = (heldItem != null && heldItem.getType() != Material.AIR);
            boolean isTaking = (composterData.getLevel() == composterData.getMaximumLevel());

            if (isPlacing || isTaking) {
                enforce(event, r, player, IslandPermission.COMPOSTER_USE);
            }
        }

        // 花盆：种植植物或取出植物时检查 FLOWER_POT_USE 权限
        // 空花盆（FLOWER_POT）时是种植，非空花盆时是取出
        if (Tag.FLOWER_POTS.isTagged(material)) {
            boolean isTaking = (material != Material.FLOWER_POT);
            boolean isPlanting = false;

            if (material == Material.FLOWER_POT) {
                ItemStack heldItem = event.getItem();
                if (heldItem != null && isPottable(heldItem.getType())) {
                    isPlanting = true;
                }
            }

            if (isPlanting || isTaking) {
                enforce(event, r, player, IslandPermission.FLOWER_POT_USE);
            }
        }
    }

    /**
     * 监听玩家左键攻击展示框事件
     * <p>
     * 玩家左键攻击有物品的展示框会弹出或破坏物品。
     * 通过 {EntityDamageByEntityEvent} 捕获此操作，
     * 检查 ITEM_FRAME_USE 权限。空框被攻击时不影响。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemFrameDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame itemFrame && event.getDamager() instanceof Player player) {
            boolean hasFramedItem = itemFrame.getItem().getType() != Material.AIR;
            if (hasFramedItem) {
                enforce(event, itemFrame.getLocation(), player, IslandPermission.ITEM_FRAME_USE);
            }
        }
    }

    /**
     * 监听玩家右键物品展示框交互事件
     * <p>
     * 玩家右键展示框可以放入物品或旋转已有物品。
     * 手持物品或展示框中已有物品时都需要检查 ITEM_FRAME_USE 权限。
     * </p>
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
                enforce(event, itemFrame.getLocation(), player, IslandPermission.ITEM_FRAME_USE);
            }
        }
    }

    /**
     * 监听末影箱打开事件
     * <p>
     * 末影箱是特殊的全局容器，其打开事件 {InventoryOpenEvent} 需要单独处理。
     * 过滤出末影箱类型的 Inventory，检查 ENDER_CHEST_OPEN 权限。
     * 同时限定只在空岛世界（主世界/下界/末地）中生效，不在其他世界拦截。
     * </p>
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
        enforce(event, loc, player, IslandPermission.ENDER_CHEST_OPEN);
    }

    /**
     * 监听打开实体容器事件
     * <p>
     * 处理非方块类的容器，包括：
     * <ul>
     *   <li>漏斗矿车 -> HOPPER_OPEN 权限</li>
     *   <li>箱子矿车、箱子船 -> CHEST_OPEN 权限</li>
     *   <li>有背包的生物（驴、骡等）-> ANIMAL_INVENTORY_OPEN 权限</li>
     * </ul>
     * 通过 {InventoryOpenEvent} 的 inventory holder 类型来判断容器类型。
     * </p>
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

        if (neededPerm != null) {
            enforce(event, loc, player, neededPerm);
        }
    }

    /**
     * 监听潜行右键打开生物背包事件
     * <p>
     * 当玩家潜行右键点击有背包的生物（驴、骡子等）时触发生物背包打开。
     * 这是 {VehicleInventoryOpen} 的补充拦截，因为直接右键交互
     * 可能不会经过 InventoryOpenEvent（如果背包尚未初始化）。
     * 同时也处理了正常打开背包时的权限检查。
     * </p>
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

        enforce(event, clicked.getLocation(), player, IslandPermission.ANIMAL_INVENTORY_OPEN);
    }

    /**
     * 根据方块类型映射对应的容器权限
     * <p>
     * 仅对实现了 {InventoryHolder} 接口的方块进行映射。
     * 箱子、陷阱箱、铜箱子统一使用 CHEST_OPEN 权限；
     * 潜影盒统一使用 SHULKER_BOX_OPEN 权限；
     * 其他容器按类型分别映射到对应的权限枚举。
     * </p>
     *
     * @param block    方块实例（用于检查是否实现了 InventoryHolder）
     * @param material 方块的材质类型
     * @return 对应的岛屿权限，非容器方块返回 null
     */
    private IslandPermission getContainerPermission(Block block, Material material) {
        if (block.getState() instanceof InventoryHolder holder) {
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
     * <p>
     * 箱子船、箱子矿车和漏斗矿车都属于带容器的载具，
     * 它们的背包权限分别映射到 CHEST_OPEN 和 HOPPER_OPEN。
     * </p>
     *
     * @param entity 要检查的实体
     * @return true 如果实体是带容器的载具
     */
    private boolean isContainerVehicle(Entity entity) {
        EntityType type = entity.getType();
        return EntityTags.CHEST_BOATS.contains(type)
                || type == EntityType.CHEST_MINECART
                || type == EntityType.HOPPER_MINECART;
    }

    /**
     * 判断物品材质是否可以种在花盆中
     * <p>
     * 原版 Minecraft 中只有特定的物品可以右键放入花盆。
     * 此处覆盖了树苗类、小型花类以及特定的植物物品。
     * </p>
     *
     * @param type 物品材质
     * @return true 如果该物品可以放入花盆
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