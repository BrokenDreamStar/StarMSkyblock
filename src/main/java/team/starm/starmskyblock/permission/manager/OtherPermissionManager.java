package team.starm.starmskyblock.permission.manager;

import io.papermc.paper.event.player.PlayerOpenSignEvent;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.inventory.EquipmentSlot;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionManager;

/**
 * 其它权限管理器
 * 处理踩踏耕地、采摘浆果、使用床等杂项权限
 */
public class OtherPermissionManager extends IslandPermissionManager {

    public OtherPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 监听玩家与杂项方块的交互事件 (踩踏、右键)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOtherInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();
        Material material = block.getType();
        Action action = event.getAction();

        if (action == Action.PHYSICAL) {
            IslandPermission permission = null;
            if (material == Material.FARMLAND) {
                permission = IslandPermission.FARMLAND_TRAMPLE;
            } else if (material == Material.TURTLE_EGG) {
                permission = IslandPermission.TURTLE_EGG_TRAMPLE;
            }

            if (permission != null && !checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
                event.setCancelled(true);
                sendDenyMessage(player, permission);
            }
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            IslandPermission permission = getRightClickPermission(material);
            if (permission != null && !checkPermission(block.getLocation(), player.getUniqueId(), permission)) {
                event.setCancelled(true);
                sendDenyMessage(player, permission);
            }
        }
    }

    /**
     * 监听打开告示牌编辑GUI行为
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignOpen(PlayerOpenSignEvent event) {
        Player player = event.getPlayer();
        Block block = event.getSign().getBlock();
        if (!checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.SIGN_EDIT)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.SIGN_EDIT);
        }
    }

    /**
     * 监听玩家完成告示牌文本编辑事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.SIGN_EDIT)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.SIGN_EDIT);
        }
    }

    /**
     * 监听玩家上床睡觉事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBed();
        if (!checkPermission(block.getLocation(), player.getUniqueId(), IslandPermission.BED_USE)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.BED_USE);
        }
    }

    /**
     * 监听实体受到伤害事件（用于拦截破坏末地水晶）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndCrystalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }

        Player player = null;
        if (event.getDamager() instanceof Player) {
            player = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                player = (Player) projectile.getShooter();
            }
        }

        if (player != null) {
            if (!checkPermission(crystal.getLocation(), player.getUniqueId(), IslandPermission.END_CRYSTAL_DAMAGE)) {
                event.setCancelled(true);
                sendDenyMessage(player, IslandPermission.END_CRYSTAL_DAMAGE);
            }
        }
    }

    /**
     * 监听玩家触发村庄袭击事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        Player player = event.getPlayer();
        if (!checkPermission(player.getLocation(), player.getUniqueId(), IslandPermission.RAID_TRIGGER)) {
            event.setCancelled(true);
            sendDenyMessage(player, IslandPermission.RAID_TRIGGER);
        }
    }

    /**
     * 辅助方法：根据方块类型获取对应的右键操作权限
     */
    private IslandPermission getRightClickPermission(Material material) {
        if (Tag.BEDS.isTagged(material)) {
            return IslandPermission.BED_USE;
        }
        return switch (material) {
            case SWEET_BERRY_BUSH, CAVE_VINES, CAVE_VINES_PLANT -> IslandPermission.SWEET_BERRY_HARVEST;
            case CAKE -> IslandPermission.CAKE_EAT;
            case RESPAWN_ANCHOR -> IslandPermission.RESPAWN_ANCHOR_USE;
            default -> null;
        };
    }
}