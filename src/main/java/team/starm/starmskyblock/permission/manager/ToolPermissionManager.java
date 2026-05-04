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
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.tag.EntityTags;
import team.starm.starmskyblock.tag.ItemTags;

import java.util.Objects;

/**
 * 工具权限管理器
 */
public class ToolPermissionManager extends IslandPermissionManager {

    /**
     * 预先缓存所有需要处理的铜变种方块，避免在高频的事件中进行字符串匹配。
     */
    public ToolPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 强制客户端同步实体状态，修复由于客户端本地预测导致的视觉假象（如装备脱落、羊毛脱落、拴绳断裂等）。
     * 延迟 1 Tick 执行，确保事件完全取消后刷新。
     *
     * @param player 目标玩家
     * @param entity 目标实体
     */
    private void syncEntityStatusForPlayer(Player player, Entity entity) {
        Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && entity.isValid()) {
                player.hideEntity(plugin, entity);
                player.showEntity(plugin, entity);
                player.updateInventory();
            }
        }, 1L);
    }

    /**
     * 判断右键方块时是否需要 SHEARS 权限
     */
    private boolean requiresShearsOnBlock(Block block) {
        if (block == null)
            return false;

        Material type = block.getType();

        if (type == Material.PUMPKIN || type == Material.CARVED_PUMPKIN) {
            return true;
        }

        if (type == Material.BEEHIVE || type == Material.BEE_NEST) {
            if (block.getBlockData() instanceof Beehive beehive) {
                return beehive.getHoneyLevel() == 5;
            }
        }

        if (type == Material.CAVE_VINES || type == Material.WEEPING_VINES ||
                type == Material.TWISTING_VINES || type == Material.KELP) {
            return true;
        }

        return false;
    }

    /**
     * 判断右键实体时是否需要 SHEARS 权限（严格检测是否穿戴了特定的装备/鞍/地毯/挽具）
     */
    private boolean requiresShearsOnEntity(Entity entity) {
        if (entity == null)
            return false;

        EntityType entityType = entity.getType();

        // 1. 有毛的羊
        if (entity instanceof Sheep sheep && !sheep.isSheared()) {
            return true;
        }

        // 2. 哞菇
        if (entityType == EntityType.MOOSHROOM) {
            return true;
        }

        // 3. 身上有蘑菇的沼骸
        if (entityType == EntityType.BOGGED) {
            if (entity instanceof Bogged bogged && !bogged.isSheared()) {
                return true;
            }
        }

        // 4. 戴南瓜头的雪傀儡
        if (entityType == EntityType.SNOW_GOLEM) {
            if (entity instanceof Snowman snowman && !snowman.isDerp()) {
                return true;
            }
        }

        // 5. 戴虞美人的铜傀儡
        if (entityType == EntityType.COPPER_GOLEM) {
            if (entity instanceof LivingEntity living) {
                ItemStack head = Objects.requireNonNull(living.getEquipment()).getHelmet();
                if (head != null && head.getType() == Material.POPPY) {
                    return true;
                }
            }
        }

        // ==================== 严格检查装备/鞍/地毯/挽具 ====================
        if (entity instanceof LivingEntity living) {
            EntityEquipment equipment = living.getEquipment();
            if (equipment == null) return false;

            // 统一判定是否已驯服或为骷髅马（与 EntityPermissionManager 保持一致）
            boolean isTamedCheckPassed = (entity instanceof Tameable tameable && tameable.isTamed())
                    || entityType == EntityType.SKELETON_HORSE || entityType == EntityType.HAPPY_GHAST;

            // 1. 检查鞍 (Saddle) 槽位
            ItemStack saddleItem = equipment.getItem(EquipmentSlot.SADDLE);
            if (!saddleItem.isEmpty() && saddleItem.getType() == Material.SADDLE) {
                if (Tag.ENTITY_TYPES_CAN_EQUIP_SADDLE.isTagged(entityType)) {
                    if (isTamedCheckPassed || entity instanceof Steerable) {
                        return true;
                    }
                }
            }

            ItemStack bodyItem = equipment.getItem(EquipmentSlot.BODY);
            if (!bodyItem.isEmpty()) {
                Material type = bodyItem.getType();
                if (isTamedCheckPassed) {
                    if (ItemTags.HORSE_ARMORS.contains(type) && Tag.ENTITY_TYPES_CAN_WEAR_HORSE_ARMOR.isTagged(entityType)) {
                        return true;
                    } else if (ItemTags.NAUTILUS_ARMORS.contains(type) && Tag.ENTITY_TYPES_CAN_WEAR_NAUTILUS_ARMOR.isTagged(entityType)) {
                        return true;
                    } else if (Tag.ITEMS_WOOL_CARPETS.isTagged(type) && entity instanceof Llama) {
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
     * 判断右键方块时是否需要 LEASH 权限（仅可拴绳的栅栏方块）
     */
    private boolean isLeashableBlock(Block block) {
        if (block == null)
            return false;
        return Tag.FENCES.isTagged(block.getType());
    }

    /**
     * 判断实体是否为可被拴绳的生物或船
     */
    private boolean isLeashableEntity(Entity entity) {
        if (entity == null)
            return false;

        EntityType type = entity.getType();
        return EntityTags.CAN_BE_LEASHED.contains(type) || EntityTags.CHEST_BOATS.contains(type)
                || Tag.ENTITY_TYPES_BOAT.isTagged(type);
    }

    /**
     * 判断玩家是否正在牵引（拴住）任何可被拴绳的生物/船（用于右手牵引时右键栅栏附加拴绳结的权限检查）
     */
    private boolean isPlayerLeadingMob(Player player) {
        if (player == null)
            return false;

        for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
            if (entity instanceof LivingEntity livingEntity && livingEntity.isLeashed()
                    && player.equals(livingEntity.getLeashHolder())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理玩家手持工具与方块交互的事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToolUse(PlayerInteractEvent event) {
        // 判断触发事件的手是否为主手，忽略副手事件
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // 获取触发事件的玩家
        Player player = event.getPlayer();
        Action action = event.getAction();

        // 特殊处理：玩家右手牵引着生物/船时右键栅栏（附加拴绳结）
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

        // 获取玩家手中的物品
        ItemStack item = event.getItem();

        // 如果手中没有物品则跳过
        if (item == null) {
            return;
        }

        // 获取手中物品类型
        Material toolType = item.getType();

        // 获取玩家交互动作
        // 对于弓和弩，只处理右键（拉弓/蓄力）行为，左键点击不拦截
        if ((toolType == Material.BOW || toolType == Material.CROSSBOW) && !action.isRightClick()) {
            return;
        }

        // 确定交互位置：如果点击了方块，取该方块的位置；否则取玩家当前位置
        Location loc = (event.getClickedBlock() != null) ? event.getClickedBlock().getLocation() : player.getLocation();

        // 初始化权限变量
        IslandPermission permission = null;

        // 获取被点击的方块对象
        Block clickedBlock = event.getClickedBlock();

        // 处理右键点击方块的场景
        if (action == Action.RIGHT_CLICK_BLOCK && clickedBlock != null) {
            // 获取被点击方块类型
            Material blockType = clickedBlock.getType();

            // 判断手持物品是否为斧头
            if (Tag.ITEMS_AXES.isTagged(toolType)) {
                // 判断方块是否为原木/木头/菌柄/菌核或竹块 用于判断去皮事件
                boolean isWoodOrLog = Tag.LOGS.isTagged(blockType) || blockType == Material.BAMBOO_BLOCK;

                // 判断方块是否为铜及其变种 判断脱蜡/除锈事件（内联方法逻辑）
                boolean isCopper = ItemTags.COPPER_VARIANTS.contains(blockType);

                // 权限判断
                if (isWoodOrLog || isCopper) {
                    permission = IslandPermission.AXE_USE;
                }
            }
            // 判断手持物品是否为锹
            else if (Tag.ITEMS_SHOVELS.isTagged(toolType)) {
                // 判断方块是否草方块/泥土/砂土/菌丝体/灰化土或缠根泥土 用于判断转换为土径事件
                boolean isDirtVariant = blockType == Material.GRASS_BLOCK || blockType == Material.DIRT ||
                        blockType == Material.COARSE_DIRT || blockType == Material.MYCELIUM ||
                        blockType == Material.PODZOL || blockType == Material.ROOTED_DIRT;

                // 判断方块是否为营火或灵魂营火 用于判断熄灭营火事件
                boolean isCampfire = blockType == Material.CAMPFIRE || blockType == Material.SOUL_CAMPFIRE;

                // 权限判断
                if (isDirtVariant || isCampfire) {
                    permission = IslandPermission.SHOVEL_USE;
                }
            }
            // 判断手持物品是否为锄头
            else if (Tag.ITEMS_HOES.isTagged(toolType)) {
                // 判断方块是否为泥土/草方块/土径或砂土/缠根泥土 用于判断转换为耕地/泥土事件
                boolean isTillable = blockType == Material.DIRT || blockType == Material.GRASS_BLOCK ||
                        blockType == Material.DIRT_PATH || blockType == Material.COARSE_DIRT ||
                        blockType == Material.ROOTED_DIRT;

                // 权限判断
                if (isTillable) {
                    permission = IslandPermission.HOE_USE;
                }
            }
            // 针对不同种类的桶的精细化方块交互检查（内联方法逻辑）
            else if (ItemTags.BUCKETS.contains(toolType)) {
                if (toolType == Material.MILK_BUCKET) {
                    // 奶桶不做限制
                    return;
                } else if (toolType == Material.BUCKET) {
                    // 空铁桶：对装满水/熔岩/细雪的炼药锅、含水方块右键时触发
                    boolean isFilledCauldron = blockType == Material.WATER_CAULDRON ||
                            blockType == Material.LAVA_CAULDRON ||
                            blockType == Material.POWDER_SNOW_CAULDRON;

                    // 检查是否为含水方块
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
                    // 水桶、熔岩桶、细雪桶及各种生物桶：对任意方块右键均触发权限检查
                    permission = IslandPermission.BUCKET_USE;
                }
            }
            // 针对玻璃瓶的权限检查
            else if (toolType == Material.GLASS_BOTTLE) {
                if (blockType == Material.WATER_CAULDRON ||
                        blockType == Material.BEEHIVE ||
                        blockType == Material.BEE_NEST) {
                    permission = IslandPermission.GLASS_BOTTLE_USE;
                }
            }
            // 剪刀方块交互（使用辅助方法，代码更清晰）
            else if (toolType == Material.SHEARS && requiresShearsOnBlock(clickedBlock)) {
                permission = IslandPermission.SHEARS_USE;
            }
            // 刷子方块交互
            else if (toolType == Material.BRUSH) {
                if (blockType == Material.SUSPICIOUS_SAND || blockType == Material.SUSPICIOUS_GRAVEL) {
                    permission = IslandPermission.BRUSH_USE;
                }
            }
            // 新增：拴绳方块交互（严格根据 wiki，仅栅栏方块才会触发拴绳打结）
            else if (toolType == Material.LEAD && isLeashableBlock(clickedBlock)) {
                permission = IslandPermission.LEASH_USE;
            }
        }

        // 如果尚未设置权限，且工具是弓或弩，且作为右键使用（包括对着空气），则赋予射箭权限
        if (permission == null && (toolType == Material.BOW || toolType == Material.CROSSBOW)
                && action.isRightClick()) {
            permission = IslandPermission.BOW_USE;
        }

        // 经过精确判断后仍未分配权限，并且工具不是已精细化处理的工具时，才使用通用方法
        // （已将 LEAD 加入已处理列表，避免在非栅栏方块上误触发权限检查）
        boolean isHandledTool = Tag.ITEMS_AXES.isTagged(toolType) || Tag.ITEMS_SHOVELS.isTagged(toolType) ||
                Tag.ITEMS_HOES.isTagged(toolType) || ItemTags.BUCKETS.contains(toolType) ||
                toolType == Material.GLASS_BOTTLE || toolType == Material.SHEARS ||
                toolType == Material.BRUSH || toolType == Material.LEAD;

        if (permission == null && !isHandledTool) {
            if (action.isRightClick()) {
                permission = getToolPermission(toolType);
            }
        }

        // 最后进行权限检查：如果需要权限且玩家在该位置没有相应权限
        if (permission != null && !checkPermission(loc, player.getUniqueId(), permission)) {
            event.setCancelled(true); // 取消事件，阻止原本的交互行为
            if (action.isRightClick()) { // 如果是右键动作，还需进一步拒绝物品使用
                event.setUseItemInHand(PlayerInteractEvent.Result.DENY); // 设置手持物品使用结果为拒绝
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) { // 若为右键点击方块且方块存在
                    event.setUseInteractedBlock(PlayerInteractEvent.Result.DENY); // 设置对方块的交互结果为拒绝
                }
            }
            sendDenyMessage(player, permission); // 发送拒绝提示
        }
    }

    /**
     * 桶操作的通用权限检查：填充（装起）或倒出（放置）时均需 BUCKET 权限，
     * 防止通过绕过 PlayerInteractEvent 直接触发桶操作。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        handleBucketEvent(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        handleBucketEvent(event);
    }

    /**
     * 统一的桶事件拦截逻辑，避免重复代码。
     */
    private void handleBucketEvent(PlayerBucketEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockClicked();
        Location location = block.getLocation();

        if (!checkPermission(location, player.getUniqueId(), IslandPermission.BUCKET_USE)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.BUCKET_USE);
        }
    }

    /**
     * 针对玩家抛竿钓鱼的动作进行精细化权限拦截
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            Player player = event.getPlayer();
            if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.FISHING_ROD_USE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.FISHING_ROD_USE);
            }
        }
    }

    /**
     * 实体射箭事件监听，用于在箭矢实际射出的瞬间进行拦截，
     * 防止玩家通过某些方式绕过右键蓄力检查。
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

            if (consumable != null && !consumable.isEmpty()) {
                originalSlot = findAmmoSlot(player, consumable);
            }

            if (consumable != null && !consumable.isEmpty()) {
                boolean isCreative = player.getGameMode() == GameMode.CREATIVE;
                ItemStack bow = event.getBow();
                boolean hasInfinity = bow != null && bow.containsEnchantment(Enchantment.INFINITY);

                if (!isCreative && !hasInfinity) {
                    ItemStack refund = consumable.clone();
                    restoreConsumable(player, originalSlot, refund);
                }
            }

            player.updateInventory();
            sendDenyMessage(player, IslandPermission.BOW_USE);
        }
    }

    /**
     * 模拟原版弹药选择规则，找出指定消耗品在玩家物品栏中的具体槽位。
     */
    private int findAmmoSlot(Player player, ItemStack ammo) {
        PlayerInventory inv = player.getInventory();
        ItemStack offhand = inv.getItemInOffHand();

        if (offhand.isSimilar(ammo)) {
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
     * 判断物品栏中指定槽位是否为空
     */
    private boolean isSlotEmpty(PlayerInventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        return item == null || item.getType().isAir();
    }

    /**
     * 在事件取消后将消耗品返还给玩家，优先放回原槽位，若原槽位已满则尝试合并或放入空位。
     */
    private void restoreConsumable(Player player, int originalSlot, ItemStack itemToRestore) {
        if (itemToRestore == null || itemToRestore.isEmpty()) {
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
     * 针对玩家手持工具右键点击实体的场景进行权限拦截。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToolUseOnEntity(PlayerInteractEntityEvent event) {
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
        if (item.isEmpty()) {
            return;
        }

        Material toolType = item.getType();
        IslandPermission permission = getToolPermission(toolType);
        EntityType entityType = clickedEntity.getType();

        // 修复：针对碗对哞菇交互触发的 BOWL_USE 权限检查
        if (toolType == Material.BOWL && entityType == EntityType.MOOSHROOM) {
            permission = IslandPermission.BOWL_USE;
        }

        // 针对不同种类的桶的精细化实体交互检查（内联方法逻辑）
        if (ItemTags.BUCKETS.contains(toolType)) {
            if (toolType == Material.BUCKET) {
                // 铁桶：只对 牛/哞菇/山羊 右键时检查权限
                if (entityType == EntityType.COW ||
                        entityType == EntityType.MOOSHROOM ||
                        entityType == EntityType.GOAT) {
                    permission = IslandPermission.BUCKET_USE;
                }
            } else if (toolType == Material.WATER_BUCKET) {
                // 水桶：只对 任意鱼/河豚/美西螈/蝌蚪 右键时检查权限
                if (entityType == EntityType.COD ||
                        entityType == EntityType.SALMON ||
                        entityType == EntityType.PUFFERFISH ||
                        entityType == EntityType.TROPICAL_FISH ||
                        entityType == EntityType.AXOLOTL ||
                        entityType == EntityType.TADPOLE) {
                    permission = IslandPermission.BUCKET_USE;
                }
            }
        }

        // 剪刀实体交互（使用辅助方法，大幅简化事件代码）
        if (toolType == Material.SHEARS) {
            if (requiresShearsOnEntity(clickedEntity)) {
                permission = IslandPermission.SHEARS_USE;
            } else {
                permission = null; // 非特殊交互不触发权限检查
            }
        }

        // 刷子实体交互
        if (toolType == Material.BRUSH) {
            if (entityType == EntityType.ARMADILLO && clickedEntity instanceof Ageable ageable && ageable.isAdult()) {
                permission = IslandPermission.BRUSH_USE;
            } else {
                permission = null; // 非特殊交互不触发权限检查
            }
        }

        // 拴绳实体交互：手持拴绳右键可以被拴住的生物/船时触发 LEASH 权限检查
        if (toolType == Material.LEAD) {
            if (isLeashableEntity(clickedEntity)) {
                permission = IslandPermission.LEASH_USE;
            } else {
                permission = null; // 非可被拴住的实体不触发权限检查
            }
        }

        if (permission != null && !checkPermission(clickedEntity.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
            syncEntityStatusForPlayer(player, clickedEntity);
        }
    }

    /**
     * 额外拦截专门的实体脱甲/剪毛事件，防止底层机制绕过右键交互
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
     * 额外拦截使用剪刀剪断拴绳的行为（PlayerUnleashEntityEvent）
     * 同时支持普通右键解开生物/船上的拴绳（使用 LEASH 权限）和用剪刀剪断（使用 SHEARS 权限）
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
     * 根据工具类型获取对应的权限
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
