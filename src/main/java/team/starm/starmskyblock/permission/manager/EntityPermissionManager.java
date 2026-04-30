package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.AbstractNautilus;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.util.TagsUtil;
import team.starm.starmskyblock.util.EntityUtil;

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
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // 获取玩家交互使用的物品
        ItemStack item = player.getInventory().getItem(event.getHand());

        boolean isEquipping = false;

        // 对马/骷髅马/僵尸马/驴/猪/炽足兽/骆驼/骆驼尸壳/鹦鹉螺/僵尸鹦鹉螺使用鞍
        if (item.getType() == Material.SADDLE) {
            if (Tag.ENTITY_TYPES_CAN_EQUIP_SADDLE.isTagged(entity.getType())) {
                if (entity instanceof LivingEntity && !EntityUtil.hasSaddle((LivingEntity) entity)) {
                    isEquipping = true;
                }
            }
        }
        // 对马/僵尸马使用马铠
        else if (TagsUtil.ITEMS_HORSE_ARMORS.contains(item.getType())) {
            if (Tag.ENTITY_TYPES_CAN_WEAR_HORSE_ARMOR.isTagged(entity.getType())) {
                isEquipping = true;
            }
        }
        // 对狼使用狼铠
        else if (item.getType() == Material.WOLF_ARMOR) {
            if (entity instanceof Wolf) {
                isEquipping = true;
            }
        }
        // 对鹦鹉螺/僵尸鹦鹉螺使用鹦鹉螺铠
        else if (TagsUtil.ITEMS_NAUTILUS_ARMORS.contains(item.getType())) {
            if (Tag.ENTITY_TYPES_CAN_WEAR_NAUTILUS_ARMOR.isTagged(entity.getType())) {
                isEquipping = true;
            }
        }
        // 对羊驼/行商羊驼使用地毯
        else if (Tag.ITEMS_WOOL_CARPETS.isTagged(item.getType())) {
            if (entity instanceof Llama) {
                isEquipping = true;
            }
        }

        // 对快乐恶魂使用挽具
        else if (Tag.ITEMS_HARNESSES.isTagged(item.getType())) {
            if (entity instanceof HappyGhast) {
                isEquipping = true;
            }
        }

        if (isEquipping) {
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

        if (isRideable(mount)) {
            if (!checkPermission(mount.getLocation(), player.getUniqueId(), IslandPermission.ENTITY_RIDE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.ENTITY_RIDE);
            }
        }
    }

    /**
     * 判断实体是否为可骑乘生物 (已使用 Abstract 类进行优化)
     */
    private boolean isRideable(Entity entity) {
        return entity instanceof AbstractHorse ||
                entity instanceof AbstractNautilus ||
                entity instanceof Pig ||
                entity instanceof Strider ||
                entity instanceof HappyGhast;
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
