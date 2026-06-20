package team.starm.starmskyblock.permission.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Bogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.tag.EntityTags;
import team.starm.starmskyblock.tag.ItemTags;

import java.util.Objects;

/**
 * 工具权限管理器
 * <p>
 * 处理玩家使用各种工具（斧、锹、锄、桶、剪刀、刷子、拴绳等）
 * 与方块或实体交互时的权限检查。本管理器是权限系统中最为复杂的
 * 一个，因为工具的右键交互行为高度多样化，需要考虑方块状态、
 * 实体装备等多种因素来决定是否需要权限以及需要何种权限。
 * </p>
 */
public class ToolPermissionManager extends BasePermissionManager {

    public ToolPermissionManager(IslandManager islandManager, ConfigManager configManager,
                                  PublicAreaConfigManager publicAreaConfig,
                                  LockedAreaConfigManager lockedAreaConfig) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
    }

    /**
     * 处理玩家手持工具与方块交互的事件
     * <p>
     * 这是工具权限管理的核心方法，处理逻辑非常复杂，因为不同工具在不同方块上的行为不同：
     * <ul>
     *   <li>斧头：剥树皮、铜块除蜡/氧化</li>
     *   <li>锹：压平草径、熄灭营火</li>
     *   <li>锄：犁地</li>
     *   <li>桶：装/放液体（牛奶桶除外）</li>
     *   <li>玻璃瓶：装水/蜂蜜</li>
     *   <li>剪刀：修剪蜂箱/南瓜/藤蔓</li>
     *   <li>刷子：刷可疑的沙/砾石</li>
     *   <li>拴绳：拴在栅栏上</li>
     *   <li>弓/弩：右键蓄力</li>
     * </ul>
     * 如果以上特定逻辑都没匹配到，则回退到 {getToolPermission} 进行通用映射。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToolUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();

        // 牵引生物时右键栅栏：在玩家牵着生物的情况下右键栅栏会拴住生物
        // 这是拴绳的特殊用法，需要提前拦截
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && isLeashableBlock(event.getClickedBlock()) && isPlayerLeadingMob(player)) {
            Location loc = event.getClickedBlock().getLocation();
            if (!checkPermission(loc, player.getUniqueId(), IslandPermission.LEASH_USE)) {
                event.setCancelled(true);
                event.setUseInteractedBlock(PlayerInteractEvent.Result.DENY);
                sendDenyMessage(player, IslandPermission.LEASH_USE);
                return;
            }
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        Material toolType = item.getType();

        // 弓/弩只处理右键
        if ((toolType == Material.BOW || toolType == Material.CROSSBOW) && action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Location loc = (event.getClickedBlock() != null) ? event.getClickedBlock().getLocation() : player.getLocation();
        IslandPermission permission = null;
        Block clickedBlock = event.getClickedBlock();

        // 右键方块时的特定工具逻辑
        if (action == Action.RIGHT_CLICK_BLOCK && clickedBlock != null) {
            Material blockType = clickedBlock.getType();

            if (Tag.ITEMS_AXES.isTagged(toolType)) {
                boolean isWoodOrLog = Tag.LOGS.isTagged(blockType) || blockType == Material.BAMBOO_BLOCK;
                boolean isCopper = ItemTags.COPPER_VARIANTS.contains(blockType);
                if (isWoodOrLog || isCopper) {
                    permission = IslandPermission.AXE_USE;
                }
            } else if (Tag.ITEMS_SHOVELS.isTagged(toolType)) {
                boolean isDirtVariant = blockType == Material.GRASS_BLOCK || blockType == Material.DIRT
                        || blockType == Material.COARSE_DIRT || blockType == Material.MYCELIUM
                        || blockType == Material.PODZOL || blockType == Material.ROOTED_DIRT;
                boolean isCampfire = blockType == Material.CAMPFIRE || blockType == Material.SOUL_CAMPFIRE;
                if (isDirtVariant || isCampfire) {
                    permission = IslandPermission.SHOVEL_USE;
                }
            } else if (Tag.ITEMS_HOES.isTagged(toolType)) {
                boolean isTillable = blockType == Material.DIRT || blockType == Material.GRASS_BLOCK
                        || blockType == Material.DIRT_PATH || blockType == Material.COARSE_DIRT
                        || blockType == Material.ROOTED_DIRT;
                if (isTillable) {
                    permission = IslandPermission.HOE_USE;
                }
            } else if (ItemTags.BUCKETS.contains(toolType)) {
                // 牛奶桶不改变世界状态，不需要权限检查
                if (toolType == Material.MILK_BUCKET) {
                    return;
                } else if (toolType == Material.BUCKET) {
                    // 空桶只对满的炼药锅、含水方块和雪块使用才检查权限
                    boolean isFilledCauldron = blockType == Material.WATER_CAULDRON
                            || blockType == Material.LAVA_CAULDRON
                            || blockType == Material.POWDER_SNOW_CAULDRON;
                    boolean isWaterloggedBlock = false;
                    if (clickedBlock.getBlockData() instanceof Waterlogged waterlogged) {
                        if (waterlogged.isWaterlogged()) {
                            isWaterloggedBlock = true;
                        }
                    }
                    if (isFilledCauldron || isWaterloggedBlock || blockType == Material.POWDER_SNOW) {
                        permission = IslandPermission.BUCKET_USE;
                    }
                } else {
                    // 装有液体的桶（水桶/熔岩桶等）
                    permission = IslandPermission.BUCKET_USE;
                }
            } else if (toolType == Material.GLASS_BOTTLE) {
                if (blockType == Material.WATER_CAULDRON
                        || blockType == Material.BEEHIVE
                        || blockType == Material.BEE_NEST) {
                    permission = IslandPermission.GLASS_BOTTLE_USE;
                }
            } else if (toolType == Material.SHEARS && requiresShearsOnBlock(clickedBlock)) {
                permission = IslandPermission.SHEARS_USE;
            } else if (toolType == Material.BRUSH) {
                if (blockType == Material.SUSPICIOUS_SAND || blockType == Material.SUSPICIOUS_GRAVEL) {
                    permission = IslandPermission.BRUSH_USE;
                }
            } else if (toolType == Material.LEAD && isLeashableBlock(clickedBlock)) {
                permission = IslandPermission.LEASH_USE;
            }
        }

        // 弓/弩的右键蓄力（可能在空中或方块上）
        if (permission == null && (toolType == Material.BOW || toolType == Material.CROSSBOW) && (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR)) {
            permission = IslandPermission.BOW_USE;
        }

        // 以上特定处理都没匹配到的工具，尝试通用映射
        boolean isHandledTool = Tag.ITEMS_AXES.isTagged(toolType) || Tag.ITEMS_SHOVELS.isTagged(toolType)
                || Tag.ITEMS_HOES.isTagged(toolType) || ItemTags.BUCKETS.contains(toolType)
                || toolType == Material.GLASS_BOTTLE || toolType == Material.SHEARS
                || toolType == Material.BRUSH || toolType == Material.LEAD;

        if (permission == null && !isHandledTool && (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR)) {
            permission = getToolPermission(toolType);
        }

        if (permission != null && !checkPermission(loc, player.getUniqueId(), permission)) {
            event.setCancelled(true);
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                event.setUseItemInHand(PlayerInteractEvent.Result.DENY);
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    event.setUseInteractedBlock(PlayerInteractEvent.Result.DENY);
                }
            }
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 监听玩家用桶装液体事件
     * <p>
     * {PlayerBucketFillEvent} 在玩家使用空桶装水/熔岩/细雪时触发。
     * 委托给 {handleBucketEvent} 统一处理。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        handleBucketEvent(event);
    }

    /**
     * 监听玩家倒出液体事件
     * <p>
     * {PlayerBucketEmptyEvent} 在玩家使用水桶/熔岩桶等放置液体时触发。
     * 委托给 {handleBucketEvent} 统一处理。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        handleBucketEvent(event);
    }

    /**
     * 统一的桶事件拦截逻辑
     * <p>
     * 装桶和倒桶使用相同的权限检查逻辑，因此提取为公共方法避免代码重复。
     * 两种操作都需要检查 BUCKET_USE 权限。
     * </p>
     *
     * @param event 桶事件实例
     */
    private void handleBucketEvent(PlayerBucketEvent event) {
        enforce(event, event.getBlockClicked().getLocation(), event.getPlayer(), IslandPermission.BUCKET_USE);
    }

    /**
     * 监听玩家钓鱼事件
     * <p>
     * 在玩家抛竿（FISHING 状态）时检查 FISHING_ROD_USE 权限。
     * 注意：收竿（CAUGHT_FISH/CAUGHT_ENTITY/INVALID）不应触发权限检查，
     * 因为权限已经在抛竿时检查过了。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            enforce(event, event.getPlayer().getLocation(), event.getPlayer(), IslandPermission.FISHING_ROD_USE);
        }
    }

    /**
     * 监听实体射箭事件
     * <p>
     * 这是 BOW_USE 权限的第二道防线。{PlayerInterceptEvent} 只能拦截
     * 右键蓄力的开始阶段，但某些情况下（如快速连发或利用客户端漏洞）
     * 可能绕过蓄力事件直接射出箭矢。{EntityShootBowEvent} 在箭矢
     * 实际射出时触发，提供了最终的拦截机会。
     * </p>
     * <p>
     * 事件取消后需要将消耗的箭矢归还给玩家：
     * 非创造模式且弓没有无限附魔时，将消耗品放回原槽位或物品栏。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Location loc = player.getLocation();
        if (!checkPermission(loc, player.getUniqueId(), IslandPermission.BOW_USE)) {
            event.setCancelled(true);

            ItemStack consumable = event.getConsumable();
            int originalSlot = -1;
            if (consumable != null && consumable.getType() != Material.AIR) {
                originalSlot = findAmmoSlot(player, consumable);
            }

            if (consumable != null && consumable.getType() != Material.AIR) {
                boolean isCreative = player.getGameMode() == GameMode.CREATIVE;
                ItemStack bow = event.getBow();
                boolean hasInfinity = bow != null && bow.containsEnchantment(Enchantment.INFINITY);
                if (!isCreative && !hasInfinity) {
                    ItemStack refund = consumable.clone();
                    restoreConsumable(player, originalSlot, refund);
                }
            }
            sendDenyMessage(player, IslandPermission.BOW_USE);
        }
    }

    /**
     * 监听玩家手持工具右键点击实体事件
     * <p>
     * 某些工具右键点击实体时需要特定权限，例如：
     * <ul>
     *   <li>碗 -> 哞菇（蘑菇煲）-> BOWL_USE</li>
     *   <li>桶 -> 牛/山羊（挤奶）-> BUCKET_USE</li>
     *   <li>水桶 -> 鱼/美西螈等 -> BUCKET_USE</li>
     *   <li>剪刀 -> 羊/哞菇/雪傀儡等（剪毛/去蘑菇）-> SHEARS_USE</li>
     *   <li>刷子 -> 成年犰狳 -> BRUSH_USE</li>
     *   <li>拴绳 -> 可拴绳实体 -> LEASH_USE</li>
     *   <li>拴绳扣（LeashKnot）-> LEASH_USE</li>
     * </ul>
     * 除此之外的实体右键交互回退到通用 {@code getToolPermission} 映射。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToolUseOnEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        if (clickedEntity instanceof LeashHitch) {
            if (!checkPermission(clickedEntity.getLocation(), player.getUniqueId(), IslandPermission.LEASH_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.LEASH_USE);
                syncEntityStatusForPlayer(player, clickedEntity);
                return;
            }
        }

        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType().isAir()) {
            return;
        }

        Material toolType = item.getType();
        IslandPermission permission = getToolPermission(toolType);
        EntityType entityType = clickedEntity.getType();

        if (toolType == Material.BOWL && entityType == EntityType.MOOSHROOM) {
            permission = IslandPermission.BOWL_USE;
        }

        if (ItemTags.BUCKETS.contains(toolType)) {
            if (toolType == Material.BUCKET) {
                if (entityType == EntityType.COW || entityType == EntityType.MOOSHROOM || entityType == EntityType.GOAT) {
                    permission = IslandPermission.BUCKET_USE;
                }
            } else if (toolType == Material.WATER_BUCKET) {
                if (entityType == EntityType.COD || entityType == EntityType.SALMON
                        || entityType == EntityType.PUFFERFISH || entityType == EntityType.TROPICAL_FISH
                        || entityType == EntityType.AXOLOTL || entityType == EntityType.TADPOLE) {
                    permission = IslandPermission.BUCKET_USE;
                }
            }
        }

        if (toolType == Material.SHEARS) {
            if (requiresShearsOnEntity(clickedEntity)) {
                permission = IslandPermission.SHEARS_USE;
            } else {
                permission = null;
            }
        }

        if (toolType == Material.BRUSH) {
            if (entityType == EntityType.ARMADILLO && clickedEntity instanceof Ageable ageable && ageable.isAdult()) {
                permission = IslandPermission.BRUSH_USE;
            } else {
                permission = null;
            }
        }

        if (toolType == Material.LEAD) {
            if (isLeashableEntity(clickedEntity)) {
                permission = IslandPermission.LEASH_USE;
            } else {
                permission = null;
            }
        }

        if (permission != null && !checkPermission(clickedEntity.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
            syncEntityStatusForPlayer(player, clickedEntity);
        }
    }

    /**
     * 监听剪刀修剪实体事件
     * <p>
     * {PlayerShearEntityEvent} 是对 {onToolUseOnEntity} 的补充拦截。
     * 某些情况（例如通过发射器激活剪刀、利用游戏机制绕过右键交互）
     * 可能不会经过 {PlayerInteractEntityEvent}，但会触发此事件。
     * 双重拦截确保 SHEARS_USE 权限的完整性。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getEntity();
        if (requiresShearsOnEntity(entity)) {
            if (!checkPermission(entity.getLocation(), player.getUniqueId(), IslandPermission.SHEARS_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.SHEARS_USE);
                syncEntityStatusForPlayer(player, entity);
            }
        }
    }

    /**
     * 监听解除拴绳事件
     * <p>
     * 玩家解开拴绳有两种方式：手持拴绳右键（收回拴绳）或手持剪刀右键（剪断）。
     * 根据主手物品类型区分所需的权限：
     * <ul>
     *   <li>剪刀 -> SHEARS_USE</li>
     *   <li>其他（通常是拴绳）-> LEASH_USE</li>
     * </ul>
     * 事件取消后需要同步实体状态以修复客户端视觉假象。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerUnleashEntity(PlayerUnleashEntityEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getEntity().getLocation();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        IslandPermission requiredPermission = (itemInHand.getType() == Material.SHEARS)
                ? IslandPermission.SHEARS_USE
                : IslandPermission.LEASH_USE;

        if (!checkPermission(loc, player.getUniqueId(), requiredPermission)) {
            event.setCancelled(true);
            sendDenyMessage(player, requiredPermission);
            syncEntityStatusForPlayer(player, event.getEntity());
        }
    }
    

    /**
     * 强制客户端同步实体状态
     * <p>
     * 当权限检查拒绝交互后，客户端可能已经本地预测了交互结果
     * （例如拴绳绑到了栅栏上或者生物身上），导致视觉上的假象。
     * 通过在下个 tick 隐藏再显示实体，强制服务器重新向客户端
     * 发送正确的实体状态数据包，消除本地预测的残留效果。
     * </p>
     */
    private void syncEntityStatusForPlayer(Player player, Entity entity) {
        Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && entity.isValid()) {
                player.hideEntity(plugin, entity);
                player.showEntity(plugin, entity);
            }
        }, 1L);
    }

    /**
     * 查找指定消耗品在玩家物品栏中的槽位
     * <p>
     * 模拟原版 Minecraft 的弹药选择优先级：
     * <ol>
     *   <li>副手（槽位 40）</li>
     *   <li>快捷栏 0-8</li>
     *   <li>背包 9-35</li>
     * </ol>
     * 用于在射箭事件取消后将消耗的弹药还到正确的槽位。
     * </p>
     *
     * @param player 玩家
     * @param ammo   需要匹配的弹药物品
     * @return 槽位号，未找到返回 -1
     */
    private int findAmmoSlot(Player player, ItemStack ammo) {
        PlayerInventory inv = player.getInventory();
        if (inv.getItemInOffHand().isSimilar(ammo)) {
            return 40;
        }
        for (int i = 0; i <= 8; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.isSimilar(ammo)) {
                return i;
            }
        }
        for (int i = 9; i <= 35; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.isSimilar(ammo)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断指定物品栏槽位是否为空
     *
     * @param inv  玩家物品栏
     * @param slot 槽位号
     * @return true 如果该槽位没有物品或物品为空气
     */
    private boolean isSlotEmpty(PlayerInventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        return item == null || item.getType().isAir();
    }

    /**
     * 将射箭消耗的弹药返还给玩家
     * <p>
     * 优先级：先尝试放回原槽位；如果原槽位被其他物品占用但可叠加则合并；
     * 最终回退到物品栏空位。这确保了玩家不会因为权限拦截而意外损失弹药。
     * </p>
     *
     * @param player         玩家
     * @param originalSlot   原弹药所在槽位
     * @param itemToRestore  要返还的物品
     */
    private void restoreConsumable(Player player, int originalSlot, ItemStack itemToRestore) {
        if (itemToRestore == null || itemToRestore.getType().isAir()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        if (originalSlot >= 0) {
            if (isSlotEmpty(inv, originalSlot)) {
                inv.setItem(originalSlot, itemToRestore);
                return;
            } else {
                ItemStack current = inv.getItem(originalSlot);
                if (current != null && current.isSimilar(itemToRestore)) {
                    current.setAmount(current.getAmount() + itemToRestore.getAmount());
                    inv.setItem(originalSlot, current);
                    return;
                }
            }
        }
        inv.addItem(itemToRestore);
    }

    /**
     * 判断右键指定方块时是否需要 SHEARS 权限
     * <p>
     * 以下情况需要使用剪刀并需要权限检查：
     * <ul>
     *   <li>南瓜/雕刻南瓜 -> 取下南瓜灯</li>
     *   <li>蜂箱/蜂巢 -> 蜜脾满级时收取蜜脾</li>
     *   <li>洞穴藤蔓/垂泪藤/缠怨藤/海带 -> 修剪（如果需要）</li>
     * </ul>
     * </p>
     *
     * @param block 目标方块
     * @return true 如果需要 SHEARS 权限
     */
    private boolean requiresShearsOnBlock(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (type == Material.PUMPKIN || type == Material.CARVED_PUMPKIN) {
            return true;
        }
        if (type == Material.BEEHIVE || type == Material.BEE_NEST) {
            if (block.getBlockData() instanceof Beehive beehive) {
                return beehive.getHoneyLevel() == 5;
            }
        }
        return type == Material.CAVE_VINES || type == Material.WEEPING_VINES
                || type == Material.TWISTING_VINES || type == Material.KELP;
    }

    /**
     * 判断右键实体时是否需要 SHEARS 权限
     * <p>
     * 剪刀可以对多种实体使用：
     * <ul>
     *   <li>羊 -> 剪毛</li>
     *   <li>哞菇 -> 去蘑菇变成牛</li>
     *   <li>沼骸（Bogged）-> 剪去蘑菇</li>
     *   <li>雪傀儡 -> 去南瓜头</li>
     *   <li>铜傀儡 -> 取下罂粟</li>
     *   <li>已驯服/可骑乘的实体 -> 取下鞍/马铠/地毯/挽具</li>
     * </ul>
     * 对每个实体类型进行详尽的检测以确保准确性。
     * </p>
     *
     * @param entity 目标实体
     * @return true 如果在该实体上使用剪刀需要 SHEARS 权限
     */
    private boolean requiresShearsOnEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        EntityType entityType = entity.getType();

        if (entity instanceof Sheep sheep && !sheep.isSheared()) {
            return true;
        }
        if (entityType == EntityType.MOOSHROOM) {
            return true;
        }
        if (entityType == EntityType.BOGGED) {
            if (entity instanceof Bogged bogged && !bogged.isSheared()) {
                return true;
            }
        }
        if (entityType == EntityType.SNOW_GOLEM) {
            if (entity instanceof Snowman snowman && !snowman.isDerp()) {
                return true;
            }
        }
        if (entityType == EntityType.COPPER_GOLEM) {
            if (entity instanceof LivingEntity living) {
                ItemStack head = Objects.requireNonNull(living.getEquipment()).getHelmet();
                if (head != null && head.getType() == Material.POPPY) {
                    return true;
                }
            }
        }

        if (entity instanceof LivingEntity living) {
            EntityEquipment equipment = living.getEquipment();
            if (equipment == null) {
                return false;
            }
            boolean isTamedCheckPassed = (entity instanceof Tameable tameable && tameable.isTamed())
                    || entityType == EntityType.SKELETON_HORSE || entityType == EntityType.HAPPY_GHAST;

            ItemStack saddleItem = equipment.getItem(EquipmentSlot.SADDLE);
            if (saddleItem.getType() != Material.AIR && saddleItem.getType() == Material.SADDLE) {
                if (Tag.ENTITY_TYPES_CAN_EQUIP_SADDLE.isTagged(entityType)) {
                    if (isTamedCheckPassed || entity instanceof Steerable) {
                        return true;
                    }
                }
            }

            ItemStack bodyItem = equipment.getItem(EquipmentSlot.BODY);
            if (bodyItem.getType() != Material.AIR) {
                Material type = bodyItem.getType();
                if (isTamedCheckPassed) {
                    if (ItemTags.HORSE_ARMORS.contains(type) && Tag.ENTITY_TYPES_CAN_WEAR_HORSE_ARMOR.isTagged(entityType)) {
                        return true;
                    } else if (ItemTags.NAUTILUS_ARMORS.contains(type) && Tag.ENTITY_TYPES_CAN_WEAR_NAUTILUS_ARMOR.isTagged(entityType)) {
                        return true;
                    } else if (Tag.WOOL_CARPETS.isTagged(type) && entity instanceof Llama) {
                        return true;
                    } else if (Tag.ITEMS_HARNESSES.isTagged(type) && entity instanceof HappyGhast) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 判断方块是否可以作为拴绳的固定点
     * <p>
     * 只有栅栏（fence）方块可以固定拴绳。
     * 使用 Bukkit 的 {Tag.FENCES} 进行判断，覆盖所有栅栏变种。
     * </p>
     *
     * @param block 目标方块
     * @return true 如果是栅栏方块
     */
    private boolean isLeashableBlock(Block block) {
        return block != null && Tag.FENCES.isTagged(block.getType());
    }

    /**
     * 判断实体是否可以被拴绳拴住
     * <p>
     * 包括所有可拴绳的生物（通过 {EntityTags.CAN_BE_LEASHED} 定义）
     * 以及箱子船和普通船。船可以被拴绳牵引。
     * </p>
     *
     * @param entity 目标实体
     * @return true 如果可以拴绳
     */
    private boolean isLeashableEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        EntityType type = entity.getType();
        return EntityTags.CAN_BE_LEASHED.contains(type) || EntityTags.CHEST_BOATS.contains(type)
                || Tag.ENTITY_TYPES_BOAT.isTagged(type);
    }

    /**
     * 判断玩家当前是否正在牵引着任何生物/船
     * <p>
     * 通过检查玩家周围 16 格范围内是否有被该玩家用拴绳牵着的生物。
     * 用于判断玩家右键栅栏时是打算拴住牵引的生物还是单纯与栅栏交互。
     * 16 格范围与拴绳的最大有效长度一致。
     * </p>
     *
     * @param player 玩家
     * @return true 如果玩家正在牵引着生物
     */
    private boolean isPlayerLeadingMob(Player player) {
        if (player == null) {
            return false;
        }
        for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
            if (entity instanceof LivingEntity livingEntity && livingEntity.isLeashed()
                    && player.equals(livingEntity.getLeashHolder())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据工具类型获取对应的通用权限
     * <p>
     * 用于处理 {onToolUse} 中未明确处理的工具类型。
     * 作为兜底映射，覆盖所有已知的工具类物品。
     * </p>
     *
     * @param toolType 工具的物品材质
     * @return 对应的岛屿权限，未知工具返回 null
     */
    private IslandPermission getToolPermission(Material toolType) {
        if (toolType == null) {
            return null;
        }
        if (Tag.ITEMS_AXES.isTagged(toolType)) {
            return IslandPermission.AXE_USE;
        }
        if (Tag.ITEMS_SHOVELS.isTagged(toolType)) {
            return IslandPermission.SHOVEL_USE;
        }
        if (Tag.ITEMS_HOES.isTagged(toolType)) {
            return IslandPermission.HOE_USE;
        }
        return switch (toolType) {
            case BOW, CROSSBOW -> IslandPermission.BOW_USE;
            case FLINT_AND_STEEL, FIRE_CHARGE -> IslandPermission.FLINT_AND_STEEL_USE;
            case SHEARS -> IslandPermission.SHEARS_USE;
            case BRUSH -> IslandPermission.BRUSH_USE;
            case LEAD -> IslandPermission.LEASH_USE;
            case BOWL -> IslandPermission.BOWL_USE;
            default -> null;
        };
    }
}