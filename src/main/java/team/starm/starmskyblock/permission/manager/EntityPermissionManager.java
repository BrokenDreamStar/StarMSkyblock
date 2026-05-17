package team.starm.starmskyblock.permission.manager;

import org.bukkit.Material;
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
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.tag.EntityTags;
import team.starm.starmskyblock.tag.ItemTags;

/**
 * 生物权限管理器
 * 处理攻击生物、喂食、装备、骑乘、交易等交互权限
 */
public class EntityPermissionManager extends BasePermissionManager {

    public EntityPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听攻击动物/怪物/村民/盔甲架事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player player = getPlayerDamager(event.getDamager());
        if (player == null) {
            return;
        }

        Entity targetEntity = event.getEntity();
        IslandPermission permission = null;

        if (targetEntity instanceof Animals || targetEntity instanceof WaterMob || targetEntity instanceof Allay) {
            permission = IslandPermission.ANIMAL_DAMAGE;
        } else if (targetEntity instanceof Monster) {
            permission = IslandPermission.MONSTER_DAMAGE;
        } else if (targetEntity instanceof AbstractVillager) {
            permission = IslandPermission.VILLAGER_DAMAGE;
        } else if (targetEntity instanceof ArmorStand) {
            permission = IslandPermission.ARMOR_STAND_DAMAGE;
        }

        if (permission != null && !checkPermission(targetEntity.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 监听操作盔甲架事件（放置/取下盔甲）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        ArmorStand armorStand = event.getRightClicked();
        if (!checkPermission(armorStand.getLocation(), player.getUniqueId(), IslandPermission.ARMOR_STAND_INTERACT)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ARMOR_STAND_INTERACT);
        }
    }

    /**
     * 监听喂食动物事件
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
            if (!checkPermission(entity.getLocation(), player.getUniqueId(), IslandPermission.ANIMAL_FEED)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.ANIMAL_FEED);
            }
        }
    }

    /**
     * 监听玩家给生物装备物品事件
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
            if (!checkPermission(entity.getLocation(), player.getUniqueId(), IslandPermission.ENTITY_EQUIP)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.ENTITY_EQUIP);
            }
        }
    }

    /**
     * 监听实体骑乘事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity mount = event.getMount();
        if (EntityTags.RIDEABLE.contains(mount.getType())) {
            if (!checkPermission(mount.getLocation(), player.getUniqueId(), IslandPermission.ENTITY_RIDE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.ENTITY_RIDE);
            }
        }
    }

    /**
     * 监听村民交易事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getRightClicked() instanceof AbstractVillager) {
            Player player = event.getPlayer();
            if (!checkPermission(event.getRightClicked().getLocation(), player.getUniqueId(), IslandPermission.VILLAGER_TRADE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.VILLAGER_TRADE);
            }
        }
    }

    /**
     * 监听猪灵以物易物事件
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

        if (!checkPermission(event.getRightClicked().getLocation(), player.getUniqueId(), IslandPermission.BARTERING)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.BARTERING);
        }
    }

    /**
     * 监听与悦灵交互事件
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
     * 从伤害源中提取玩家实例（支持直接攻击与投射物）
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
     * 判断物品是否为指定动物的繁殖/驯服/交互食物
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
        }
        return false;
    }
}