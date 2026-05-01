package team.starm.starmskyblock.permission.manager;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;
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
     * 监听实体受损事件（保护岛屿上的动物/怪物/村民）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player))
            return;
        if (!event.getEntity().getWorld().getName().startsWith("skyblock"))
            return;

        Entity entity = event.getEntity();
        IslandPermission permission = (entity instanceof Animals) ? IslandPermission.ANIMAL_DAMAGE
                : (entity instanceof Monster) ? IslandPermission.MONSTER_DAMAGE
                : (entity instanceof AbstractVillager) ? IslandPermission.VILLAGER_DAMAGE
                : null;

        if (permission != null && !checkPermission(entity.getLocation(), player.getUniqueId(), permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 监听喂食动物事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player))
            return;

        if (!checkPermission(event.getMother().getLocation(), player.getUniqueId(), IslandPermission.ANIMAL_FEEDING)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.ANIMAL_FEEDING);
        }
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
                || entityType == EntityType.SKELETON_HORSE;

        EquipmentSlot targetSlot = null;

        if (type == Material.SADDLE && Tag.ENTITY_TYPES_CAN_EQUIP_SADDLE.isTagged(entityType)) {
            if (isTamedCheckPassed || entity instanceof Steerable) {
                targetSlot = EquipmentSlot.SADDLE;
            }
        } else if (isTamedCheckPassed) {
            if (ItemTags.HORSE_ARMORS.contains(type) && Tag.ENTITY_TYPES_CAN_WEAR_HORSE_ARMOR.isTagged(entityType)) {
                targetSlot = EquipmentSlot.BODY;
            }
            // 玩家只能对自己驯服的狼交互 这里无需处理
//            else if (type == Material.WOLF_ARMOR && entity instanceof Wolf) {
//                targetSlot = EquipmentSlot.BODY;
//            }
            else if (ItemTags.NAUTILUS_ARMORS.contains(type) && Tag.ENTITY_TYPES_CAN_WEAR_NAUTILUS_ARMOR.isTagged(entityType)) {
                targetSlot = EquipmentSlot.BODY;
            } else if (Tag.ITEMS_WOOL_CARPETS.isTagged(type) && entity instanceof Llama) {
                targetSlot = EquipmentSlot.BODY;
            } else if (Tag.ITEMS_HARNESSES.isTagged(type) && entity instanceof HappyGhast) {
                targetSlot = EquipmentSlot.BODY;
            }
        }

        if (targetSlot != null && equipment.getItem(targetSlot).isEmpty()) {
            if (!checkPermission(entity.getLocation(), player.getUniqueId(), IslandPermission.ENTITY_EQUIP)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.ENTITY_EQUIP);
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

    public boolean canBreedAnimal(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ANIMAL_FEEDING);
    }

    public boolean canDamageAnimal(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ANIMAL_DAMAGE);
    }

    public boolean canDamageMonster(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.MONSTER_DAMAGE);
    }

    public boolean canDamageVillager(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.VILLAGER_DAMAGE);
    }

    /**
     * 检查是否可以骑乘生物
     */
    public boolean canRideEntity(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ENTITY_RIDE);
    }

    /**
     * 检查是否可以给生物装备物品
     */
    public boolean canEquipEntity(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.ENTITY_EQUIP);
    }

    /**
     * 检查是否可以与村民交易
     */
    public boolean canTradeWithVillager(Location location, UUID uuid) {
        return checkPermission(location, uuid, IslandPermission.VILLAGER_TRADING);
    }
}
