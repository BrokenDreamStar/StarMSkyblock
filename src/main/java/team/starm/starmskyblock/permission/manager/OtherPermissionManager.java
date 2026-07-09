package team.starm.starmskyblock.permission.manager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.tag.ItemTags;

import java.util.Optional;

/**
 * 其它权限管理器
 * 处理踩踏耕地、采摘浆果、使用床等杂项权限
 */
public class OtherPermissionManager extends BasePermissionManager {

    public OtherPermissionManager(IslandManager islandManager, ConfigManager configManager,
                                   PublicAreaConfigManager publicAreaConfig,
                                   LockedAreaConfigManager lockedAreaConfig) {
        super(islandManager, configManager, publicAreaConfig, lockedAreaConfig);
    }

    /**
     * 监听玩家与杂项方块的交互事件
     * <p>
     * 处理两类交互：
     * <ul>
     *   <li>物理交互（踩踏）：耕地和海龟蛋的踩踏保护</li>
     *   <li>右键交互：床、蛋糕、重生锚、甜浆果丛/发光浆果的采摘</li>
     * </ul>
     * </p>
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

            if (permission != null) {
                enforce(event, block.getLocation(), player, permission);
            }
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            IslandPermission permission = getRightClickPermission(block);
            if (permission != null) {
                enforce(event, block.getLocation(), player, permission);
            }
        }
    }

    /**
     * 监听告示牌文本编辑完成事件
     * <p>
     * {SignChangeEvent} 在玩家编辑告示牌后点击"完成"时触发。
     * 在此处检查 SIGN_EDIT 权限。注意：打开编辑界面的操作
     * 不在此处拦截（由其他事件处理）。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        enforce(event, event.getBlock().getLocation(), event.getPlayer(), IslandPermission.SIGN_EDIT);
    }

    /**
     * 监听玩家上床睡觉事件
     * <p>
     * {PlayerBedEnterEvent} 在玩家尝试上床睡觉时触发。
     * 检查 BED_USE 权限。该权限独立于其他容器/方块交互权限。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        enforce(event, event.getBed().getLocation(), event.getPlayer(), IslandPermission.BED_USE);
    }

    /**
     * 监听末地水晶被破坏事件
     * <p>
     * 末地水晶是一种实体（EnderCrystal），通过 {EntityDamageByEntityEvent}
     * 拦截对其造成的伤害。支持直接近战攻击和投射物攻击。
     * 检查 END_CRYSTAL_DAMAGE 权限。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndCrystalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }

        Player player = null;
        if (event.getDamager() instanceof Player damagerPlayer) {
            player = damagerPlayer;
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooterPlayer) {
                player = shooterPlayer;
            }
        }

        if (player != null) {
            enforce(event, crystal.getLocation(), player, IslandPermission.END_CRYSTAL_DAMAGE);
        }
    }

    /**
     * 监听玩家触发村庄袭击事件
     * <p>
     * {RaidTriggerEvent} 在玩家触发袭击（进入带有不祥之兆效果的村庄）时触发。
     * 检查 RAID_TRIGGER 权限，防止玩家在他人岛屿上触发袭击破坏村庄。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        enforce(event, event.getPlayer().getLocation(), event.getPlayer(), IslandPermission.RAID_TRIGGER);
    }

    /**
     * 监听玩家使用刷怪蛋事件
     * <p>
     * 右键使用刷怪蛋时检查 SPAWN_EGG_USE 权限。
     * 支持右键方块和右键空气两种使用方式。
     * 使用 {ItemTags.SPAWN_EGGS} 判断物品是否为刷怪蛋，
     * 覆盖所有生物类型的刷怪蛋。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !ItemTags.SPAWN_EGGS.contains(item.getType())) {
            return;
        }

        Location loc = (event.getClickedBlock() != null)
                ? event.getClickedBlock().getLocation()
                : player.getLocation();
        enforce(event, loc, player, IslandPermission.SPAWN_EGG_USE);
    }

    /**
     * 监听玩家右键实体使用刷怪蛋事件
     * <p>
     * 右键点击生物使用刷怪蛋可以转换生物类型（如将僵尸转化为溺尸）。
     * 这是 {onSpawnEggUse} 的补充，因为右键实体的事件路径与右键方块不同。
     * </p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnEggUseOnEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item.getType().isAir() || !ItemTags.SPAWN_EGGS.contains(item.getType())) {
            return;
        }

        Location loc = event.getRightClicked().getLocation();
        enforce(event, loc, player, IslandPermission.SPAWN_EGG_USE);
    }

    /**
     * 监听玩家进入传送门事件
     * <p>
     * 在 LOW 优先级处理，先于 PortalListener 的 NORMAL 优先级执行。
     * 检查 ENTER_NETHER_PORTAL 和 ENTER_END_PORTAL 权限，
     * 未通过时取消事件并发送权限拒绝消息。
     * </p>
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        TeleportCause cause = event.getCause();

        if (cause != TeleportCause.NETHER_PORTAL && cause != TeleportCause.END_PORTAL) {
            return;
        }

        Location from = event.getFrom();
        World fromWorld = from.getWorld();
        if (fromWorld == null) return;

        // 只在空岛世界进行检查
        String worldName = fromWorld.getName();
        boolean isSkyblockWorld = worldName.equals(configManager.getWorldNameNormal())
                || worldName.equals(configManager.getWorldNameNether())
                || worldName.equals(configManager.getWorldNameEnd());
        if (!isSkyblockWorld) return;

        // 查找传送门所在岛屿
        int chunkX = from.getChunk().getX();
        int chunkZ = from.getChunk().getZ();
        Optional<Island> optionalIsland = islandManager.getIslandAt(chunkX, chunkZ);
        if (optionalIsland.isEmpty()) {
            optionalIsland = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
        }
        if (optionalIsland.isEmpty()) {
            // 回退：按玩家关联查找
            optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
        }
        if (optionalIsland.isEmpty()) return;

        IslandPermission permission = (cause == TeleportCause.NETHER_PORTAL)
                ? IslandPermission.ENTER_NETHER_PORTAL
                : IslandPermission.ENTER_END_PORTAL;

        // OP 或拥有 skyblock.bypass 权限节点的玩家可以绕过传送门权限检查
        if (player.isOp() || player.hasPermission("skyblock.bypass")) {
            return;
        }

        if (!optionalIsland.get().hasPermission(player.getUniqueId(), permission)) {
            event.setCancelled(true);
            // 此分支未走标准 resolve 路径，清除该玩家上次的解析缓存，使 sendDenyMessage 走默认 no-permission 提示
            lastCheckResult.remove(player.getUniqueId());
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 根据方块类型和状态获取对应的右键操作权限
     * <p>
     * 处理床、甜浆果丛、发光浆果、蛋糕、重生锚的右键权限映射。
     * 对于结果类植物（甜浆果丛和发光浆果），只在已结果的状态下
     * 才返回 SWEET_BERRY_HARVEST 权限；未成熟时返回 null 表示不需要检查。
     * 这是为了避免在植物尚未结果时误拦截玩家的正常交互。
     * </p>
     *
     * @param block 目标方块
     * @return 对应的岛屿权限，不需要检查时返回 null
     */
    @SuppressWarnings("deprecation")
    private IslandPermission getRightClickPermission(Block block) {
        Material material = block.getType();
        if (Tag.BEDS.isTagged(material)) {
            return IslandPermission.BED_USE;
        }

        // 甜浆果丛：只有已结果的状态才需要权限检查
        if (material == Material.SWEET_BERRY_BUSH) {
            if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
                return null;
            }
            return IslandPermission.SWEET_BERRY_HARVEST;
        }
        // 发光浆果：只有已结果的状态才需要权限检查
        if (material == Material.CAVE_VINES || material == Material.CAVE_VINES_PLANT) {
            boolean hasBerries;
            if (block.getBlockData() instanceof CaveVines vines) {
                hasBerries = vines.isBerries();
            } else if (block.getBlockData() instanceof CaveVinesPlant plant) {
                hasBerries = plant.isBerries();
            } else {
                return IslandPermission.SWEET_BERRY_HARVEST;
            }
            if (!hasBerries) {
                return null;
            }
            return IslandPermission.SWEET_BERRY_HARVEST;
        }

        return switch (material) {
            case CAKE -> IslandPermission.CAKE_EAT;
            case RESPAWN_ANCHOR -> IslandPermission.RESPAWN_ANCHOR_USE;
            default -> null;
        };
    }
}