package team.starm.starmskyblock.permission.manager;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Tag;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.tag.EntityTags;
import team.starm.starmskyblock.tag.ItemTags;

/**
 * 生物权限管理器
 * 处理攻击生物、喂食、装备、骑乘、交易等交互权限
 */
public class EntityPermissionManager extends BasePermissionManager {

    public EntityPermissionManager(IslandManager islandManager, ConfigManager configManager,
                                    PublicAreaConfigManager publicAreaConfig,
                                    LockedAreaConfigManager lockedAreaConfig,
                                    JavaPlugin plugin, SkyblockWorldManager worldManager) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig, plugin, worldManager);
    }

    /**
     * 监听实体受到伤害事件
     * <p>
     * 根据目标实体的类型（动物/水生生物/悦灵、怪物、村民、盔甲架）
     * 分别映射到不同的权限枚举。通过 {instanceof} 判断实体类型，
     * 确保使用 Bukkit 的继承体系而不是 EntityType 枚举，以兼容
     * 自定义生物和未来的 Minecraft 版本。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player player = getPlayerDamager(event.getDamager());
        if (player == null) {
            return;
        }

        Entity targetEntity = event.getEntity();
        IslandPermission permission = switch (targetEntity) {
            case Animals a -> IslandPermission.ANIMAL_DAMAGE;
            case WaterMob w -> IslandPermission.ANIMAL_DAMAGE;
            case Allay al -> IslandPermission.ANIMAL_DAMAGE;
            case Monster m -> IslandPermission.MONSTER_DAMAGE;
            case AbstractVillager v -> IslandPermission.VILLAGER_DAMAGE;
            case ArmorStand a -> IslandPermission.ARMOR_STAND_DAMAGE;
            default -> null;
        };

        if (permission != null) {
            enforce(event, targetEntity.getLocation(), player, permission);
        }
    }

    /**
     * 监听操作盔甲架事件
     * <p>
     * {PlayerArmorStandManipulateEvent} 在玩家给盔甲架装备或取下
     * 物品时触发。此操作使用独立的 ARMOR_STAND_INTERACT 权限，
     * 与攻击盔甲架（ARMOR_STAND_DAMAGE）区分开。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        enforce(event, event.getRightClicked().getLocation(), event.getPlayer(),
                IslandPermission.ARMOR_STAND_INTERACT);
    }

    /**
     * 监听喂食动物事件
     * <p>
     * 玩家右键动物并手持该动物的繁殖/驯服食物时，
     * 检查 ANIMAL_FEED 权限。喂食操作可能改变动物状态
     * （繁殖、驯服、生长），因此需要权限控制。
     * 喂食判定委托给 {isAnimalFeedItem} 方法。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalFeedInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType().isAir()) {
            return;
        }

        if (isAnimalFeedItem(entity, item.getType())) {
            enforce(event, entity.getLocation(), player, IslandPermission.ANIMAL_FEED);
        }
    }

    /**
     * 监听玩家给生物装备物品事件
     * <p>
     * 处理给可装备的生物（马、驴、骡、羊驼、炽足兽等）装备
     * 鞍、马铠、地毯、箱子、挽具等物品的权限检查。
     * 需要判断：
     * <ul>
     *   <li>物品类型是否可以装备到该生物上</li>
     *   <li>生物是否已驯服（或为骷髅马/幸福恶魂等特殊类型）</li>
     *   <li>目标装备槽位是否为空（不为空则视为替换操作，同样需要权限）</li>
     * </ul>
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityEquip(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType().isAir() || !(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment == null) {
            return;
        }

        EntityType entityType = entity.getType();
        boolean isTamedCheckPassed = (entity instanceof Tameable tameable && tameable.isTamed())
                || entityType == EntityType.SKELETON_HORSE || entityType == EntityType.HAPPY_GHAST;

        EquipmentSlot targetSlot = null;
        boolean isEquippingChest = false;

        if (item.getType() == Material.SADDLE && Tag.ENTITY_TYPES_CAN_EQUIP_SADDLE.isTagged(entityType)) {
            if (isTamedCheckPassed || entity instanceof Steerable) {
                targetSlot = EquipmentSlot.SADDLE;
            }
        } else if (isTamedCheckPassed) {
            if (ItemTags.HORSE_ARMORS.contains(item.getType()) && Tag.ENTITY_TYPES_CAN_WEAR_HORSE_ARMOR.isTagged(entityType)) {
                targetSlot = EquipmentSlot.BODY;
            } else if (ItemTags.NAUTILUS_ARMORS.contains(item.getType()) && Tag.ENTITY_TYPES_CAN_WEAR_NAUTILUS_ARMOR.isTagged(entityType)) {
                targetSlot = EquipmentSlot.BODY;
            } else if (Tag.WOOL_CARPETS.isTagged(item.getType()) && entity instanceof Llama) {
                targetSlot = EquipmentSlot.BODY;
            } else if (Tag.ITEMS_HARNESSES.isTagged(item.getType()) && entity instanceof HappyGhast) {
                targetSlot = EquipmentSlot.BODY;
            } else if (item.getType() == Material.CHEST && entity instanceof ChestedHorse chestedHorse && !chestedHorse.isCarryingChest()) {
                isEquippingChest = true;
            }
        }

        if ((targetSlot != null && equipment.getItem(targetSlot).getType().isAir()) || isEquippingChest) {
            enforce(event, entity.getLocation(), player, IslandPermission.ENTITY_EQUIP);
        }
    }

    /**
     * 监听实体骑乘事件
     * <p>
     * {EntityMountEvent} 在玩家骑上生物时触发。
     * 只有 {EntityTags.RIDEABLE} 中定义的生物类型才受检查。
     * 包括马、猪、炽足兽、骷髅马等可骑乘生物。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity mount = event.getMount();
        if (EntityTags.RIDEABLE.contains(mount.getType())) {
            enforce(event, mount.getLocation(), player, IslandPermission.ENTITY_RIDE);
        }
    }

    /**
     * 监听村民交易事件
     * <p>
     * 右键村民（或流浪商人）打开交易界面时检查 VILLAGER_TRADE 权限。
     * 使用 {AbstractVillager} 作为判断依据，同时覆盖普通村民和流浪商人。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getRightClicked() instanceof AbstractVillager) {
            enforce(event, event.getRightClicked().getLocation(), event.getPlayer(),
                    IslandPermission.VILLAGER_TRADE);
        }
    }

    /**
     * 监听猪灵以物易物事件
     * <p>
     * 玩家右键成年猪灵并手持金锭时触发以物易物，
     * 检查 BARTERING 权限。幼年猪灵不会触发以物易物，
     * 因此跳过检查。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBartering(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Piglin piglin) || !piglin.isAdult()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType() != Material.GOLD_INGOT) {
            return;
        }

        enforce(event, event.getRightClicked().getLocation(), player, IslandPermission.BARTERING);
    }

    /**
     * 监听与悦灵交互事件
     * <p>
     * 悦灵的交互逻辑较为特殊：
     * <ul>
     *   <li>玩家手持物品且悦灵空手 -> 将物品给悦灵</li>
     *   <li>玩家空手且悦灵手持物品 -> 从悦灵取回物品</li>
     *   <li>双方都手持物品或都空手 -> 无需检查</li>
     * </ul>
     * 以上两种情况需要检查 ALLAY_INTERACT 权限。
     * 拴绳对悦灵的使用在此处跳过（由工具管理器处理）。
     * 额外处理副手误触：如果主手有物品但副手触发交互，不发送拒绝消息。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAllayInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Allay allay)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack interactItem = player.getInventory().getItem(event.getHand());
        boolean playerHoldingItem = !interactItem.getType().isAir();

        if (playerHoldingItem && interactItem.getType() == Material.LEAD) {
            return;
        }

        ItemStack allayItem = allay.getEquipment().getItemInMainHand();
        boolean allayHoldingItem = !allayItem.getType().isAir();
        boolean shouldCheck = (playerHoldingItem && !allayHoldingItem) || (!playerHoldingItem && allayHoldingItem);

        if (shouldCheck) {
            if (!checkPermission(event.getRightClicked().getLocation(), player.getUniqueId(), IslandPermission.ALLAY_INTERACT)) {
                event.setCancelled(true);
                boolean isAccidentalOffHand = false;
                if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    ItemStack mainHandItem = player.getInventory().getItemInMainHand();
                    if (!mainHandItem.getType().isAir()) {
                        isAccidentalOffHand = true;
                    }
                }
                if (!isAccidentalOffHand) {
                    sendDenyMessage(player, IslandPermission.ALLAY_INTERACT);
                }
            }
        }
    }

    /**
     * 从伤害源中提取玩家实例
     * <p>
     * 伤害源可能是玩家直接攻击（近战/弓），也可能是投射物间接攻击。
     * 此方法统一处理这两种情况，返回发起攻击的玩家实例。
     * </p>
     *
     * @param damager 伤害源实体
     * @return 攻击者玩家实例，如果非玩家攻击则返回 null
     */
    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    /**
     * 判断物品是否是指定动物的繁殖/驯服/交互食物
     * <p>
     * 除了使用 Bukkit 内置的 {Animal.isBreedItem} 判断外，
     * 还补充处理了以下特殊情况：
     * <ul>
     *   <li>狼驯服：骨头（仅未驯服的狼）</li>
     *   <li>鹦鹉驯服：曲奇</li>
     *   <li>幼年动物催熟：金蒲公英/金胡萝卜</li>
     * </ul>
     * 这些特殊情况在 Bukkit API 中可能未被覆盖。
     * </p>
     *
     * @param entity   目标动物实体
     * @param material 玩家手持物品的材质
     * @return true 如果该物品可以喂给该动物
     */
    private boolean isAnimalFeedItem(Entity entity, Material material) {
        if (!(entity instanceof Animals animal)) {
            return false;
        }
        if (animal.isBreedItem(material)) {
            return true;
        }
        if (animal instanceof Wolf wolf && material == Material.BONE && !wolf.isTamed()) {
            return true;
        } else if (animal instanceof Parrot && material == Material.COOKIE) {
            return true;
        } else if (material == Material.GOLDEN_DANDELION && !animal.isAdult()) {
            return true;
        }
        return false;
    }
}