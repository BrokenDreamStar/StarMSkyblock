package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EntityEquipment;


import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.tag.EntityTags;
import team.starm.starmskyblock.tag.ItemTags;

/**
 * 生物权限管理器
 */
public class EntityPermissionManager extends IslandPermissionManager {

    public EntityPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }


    /**
     * 监听攻击动物/怪物/村民事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {

        Entity damagerEntity = event.getDamager();
        Player player = null;

        if (damagerEntity instanceof Player p) {
            player = p;
        } else if (damagerEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            player = p;
        }

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
        }

        if (permission != null && !checkPermission(targetEntity.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }


    /**
     * 监听喂食动物事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalFeedInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());

        if (item.getType().isAir()) {
            return;
        }

        Material material = item.getType();

        if (isAnimalFeedItem(entity, material)) {
            if (!checkPermission(entity.getLocation(), player.getUniqueId(), IslandPermission.ANIMAL_FEEDING)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.ANIMAL_FEEDING);
            }
        }
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

    /**
     * 监听玩家给生物装备物品事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityEquip(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        Material type = item.getType();

        if (type.isAir() || !(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment == null) return;

        EntityType entityType = entity.getType();
        boolean isTamedCheckPassed = (entity instanceof Tameable tameable && tameable.isTamed())
                || entityType == EntityType.SKELETON_HORSE || entityType == EntityType.HAPPY_GHAST;

        EquipmentSlot targetSlot = null;
        boolean isEquippingChest = false;

        if (type == Material.SADDLE && Tag.ENTITY_TYPES_CAN_EQUIP_SADDLE.isTagged(entityType)) {
            if (isTamedCheckPassed || entity instanceof Steerable) {
                targetSlot = EquipmentSlot.SADDLE;
            }
        } else if (isTamedCheckPassed) {
            if (ItemTags.HORSE_ARMORS.contains(type) && Tag.ENTITY_TYPES_CAN_WEAR_HORSE_ARMOR.isTagged(entityType)) {
                targetSlot = EquipmentSlot.BODY;
            } else if (ItemTags.NAUTILUS_ARMORS.contains(type) && Tag.ENTITY_TYPES_CAN_WEAR_NAUTILUS_ARMOR.isTagged(entityType)) {
                targetSlot = EquipmentSlot.BODY;
            } else if (Tag.ITEMS_WOOL_CARPETS.isTagged(type) && entity instanceof Llama) {
                targetSlot = EquipmentSlot.BODY;
            } else if (Tag.ITEMS_HARNESSES.isTagged(type) && entity instanceof HappyGhast) {
                targetSlot = EquipmentSlot.BODY;
            } else if (type == Material.CHEST && entity instanceof ChestedHorse chestedHorse && !chestedHorse.isCarryingChest()) {
                isEquippingChest = true;
            }
        }

        if ((targetSlot != null && equipment.getItem(targetSlot).isEmpty()) || isEquippingChest) {
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
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (entity instanceof AbstractVillager) {
            if (!checkPermission(entity.getLocation(), player.getUniqueId(), IslandPermission.VILLAGER_TRADING)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.VILLAGER_TRADING);
            }
        }
    }

    /**
     * 监听猪灵以物易物事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBartering(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Piglin piglin) || !piglin.isAdult()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());

        if (item.getType() != Material.GOLD_INGOT) {
            return;
        }

        if (!checkPermission(entity.getLocation(), player.getUniqueId(), IslandPermission.BARTERING)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.BARTERING);
        }
    }

    /**
     * 监听与悦灵交互事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAllayInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Allay allay)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack interactItem = player.getInventory().getItem(event.getHand());

        boolean playerHoldingItem = !interactItem.getType().isAir();

        if (playerHoldingItem && interactItem.getType() == Material.LEAD) {
            return;
        }

        allay.getEquipment();
        ItemStack allayItem = allay.getEquipment().getItemInMainHand();
        boolean allayHoldingItem = !allayItem.getType().isAir();

        boolean shouldCheck = (playerHoldingItem && !allayHoldingItem) || (!playerHoldingItem && allayHoldingItem);

        if (shouldCheck) {
            if (!checkPermission(entity.getLocation(), player.getUniqueId(), IslandPermission.ALLAY)) {
                event.setCancelled(true);

                boolean isAccidentalOffHand = false;
                if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    ItemStack mainHandItem = player.getInventory().getItemInMainHand();
                    if (!mainHandItem.getType().isAir()) {
                        isAccidentalOffHand = true;
                    }
                }

                if (!isAccidentalOffHand) {
                    sendDenyMessage(player, IslandPermission.ALLAY);
                }
            }
        }
    }
    
}
