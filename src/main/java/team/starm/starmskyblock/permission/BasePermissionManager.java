package team.starm.starmskyblock.permission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Listener;

import org.bukkit.entity.Player;

import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.config.LockedAreaConfigManager;
import team.starm.starmskyblock.config.PublicAreaConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;

/**
 * 权限检查基类
 * <p>
 * 所有具体权限管理器的抽象基类，提供统一的权限检查逻辑和拒绝消息发送机制。
 * 封装了以下通用功能：
 * <ul>
 *   <li>根据玩家位置和 UUID 查询所属岛屿并进行权限判断</li>
 *   <li>区域锁定检测（未解锁的岛屿区块）</li>
 *   <li>公共区域检测</li>
 *   <li>权限拒绝消息发送（带冷却控制，防止刷屏）</li>
 * </ul>
 * </p>
 */
public abstract class BasePermissionManager implements Listener {

    /** 岛屿管理器，用于根据坐标查询岛屿 */
    protected final IslandManager islandManager;
    /** 配置管理器，用于获取世界名称和消息冷却时间等配置 */
    protected final ConfigManager configManager;
    /** 公共区域配置管理器 */
    protected final PublicAreaConfigManager publicAreaConfig;
    /** 未解锁区域配置管理器 */
    protected final LockedAreaConfigManager lockedAreaConfig;
    /** 记录每个玩家最近一次收到权限拒绝消息的时间戳，用于消息冷却防刷屏 */
    protected final Map<UUID, Long> lastDenyMessageTime = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Long> eldest) {
            return size() > 256;
        }
    };
    /** 上次权限检查结果：是否因为区域未锁定而拒绝 */
    protected boolean lastCheckWasAreaLocked = false;
    /** 上次权限检查结果：是否因为处于公共区域而拒绝 */
    protected boolean lastCheckWasPublicArea = false;
    /** 上次触发区域锁定的岛屿引用（用于发送更精准的提示消息） */
    protected Island lastAreaLockedIsland = null;

    public BasePermissionManager(IslandManager islandManager, ConfigManager configManager,
                                  PublicAreaConfigManager publicAreaConfig,
                                  LockedAreaConfigManager lockedAreaConfig) {
        this.islandManager = islandManager;
        this.configManager = configManager;
        this.publicAreaConfig = publicAreaConfig;
        this.lockedAreaConfig = lockedAreaConfig;
    }

    /**
     * 静态权限检查方法
     */
    public static boolean hasPermission(Island island, UUID uuid, IslandPermission permission) {
        if (island == null) {
            return true;
        }
        return island.hasPermission(uuid, permission);
    }

    /**
     * 根据坐标获取当前位置所属的岛屿对象（委托至 IslandManager）
     */
    public static Optional<Island> getPlayerCurrentIsland(IslandManager islandManager, Location location) {
        return islandManager.getIslandAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * 根据坐标获取当前所属岛屿（使用最大范围，用于权限判断）
     */
    public static Optional<Island> getPlayerCurrentIslandMaxRange(IslandManager islandManager, Location location) {
        return islandManager.getIslandAtMaxRange(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * 统一的权限检查方法（使用最大岛屿范围）
     */
    public boolean checkPermission(Location location, UUID uuid, IslandPermission permission) {
        // OP 或拥有 skyblock.bypass 权限节点的玩家可以绕过所有权限检查
        Player bypassPlayer = Bukkit.getPlayer(uuid);
        if (bypassPlayer != null && (bypassPlayer.isOp() || bypassPlayer.hasPermission("skyblock.bypass"))) {
            return true;
        }
        if (bypassPlayer == null) {
            // 离线玩家 —— 退化为无 bypass 路径(岛屿权限仍可基于 UUID 判定)
            PermissionCheckResult r = resolveOffline(location, uuid);
            return check(r, permission);
        }
        return check(resolve(location, bypassPlayer), permission);
    }

    /**
     * 权限检查重载 —— 当调用方已持有 Player 引用时直接传入,免去内部 Bukkit.getPlayer(uuid) 反查。
     */
    public boolean checkPermission(Location location, Player player, IslandPermission permission) {
        if (player.isOp() || player.hasPermission("skyblock.bypass")) {
            return true;
        }
        return check(resolve(location, player), permission);
    }

    /**
     * 子监听器样板收敛：检查权限失败时取消事件并发送拒绝消息。
     * <p>
     * 替代每个子监听器里重复出现的
     * {@code if (!checkPermission(loc, player, perm)) { event.setCancelled(true); sendDenyMessage(player, perm); }}
     * 三行样板，使每个事件处理器聚焦于"提取 Location + 决定权限类型"两件事。
     */
    protected void enforce(Cancellable event, Location location, Player player, IslandPermission permission) {
        if (!checkPermission(location, player, permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 子监听器样板收敛（已解析 result 重载）：复用同一 {@link PermissionCheckResult}
     * 进行多次权限判定时使用，避免每次重复 Location→Island 解析。
     * 典型场景见 {@code ContainerPermissionManager.onContainerInteract}。
     */
    protected void enforce(Cancellable event, PermissionCheckResult r, Player player, IslandPermission permission) {
        if (!check(r, permission)) {
            event.setCancelled(true);
            sendDenyMessage(player, permission);
        }
    }

    /**
     * 单次解析 Location → 玩家/岛屿/锁定/公共区域状态,产出不可变 {@link PermissionCheckResult}。
     * 同一事件内多次权限检查可复用同一 result,避免重复执行 Bukkit.getPlayer、island grid 索引、
     * chunk 坐标计算等开销(原实现每次 checkPermission 都重复 7-8 次同样的解析)。
     */
    public PermissionCheckResult resolve(Location location, Player player) {
        if (player.isOp() || player.hasPermission("skyblock.bypass")) {
            return PermissionCheckResult.bypass(player);
        }
        String worldName = location.getWorld().getName();
        if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(worldName)) {
            boolean inPublicWorld = StarMSkyblock.getInstance().getWorldManager().isPublicWorld(worldName);
            lastCheckWasAreaLocked = false;
            lastCheckWasPublicArea = inPublicWorld;
            return PermissionCheckResult.outsideSkyblockWorld(player, inPublicWorld);
        }

        Optional<Island> optIsland = getPlayerCurrentIslandMaxRange(islandManager, location);

        if (optIsland.isEmpty()) {
            lastCheckWasAreaLocked = false;
            lastCheckWasPublicArea = true;
            return PermissionCheckResult.publicArea(player);
        }

        Island island = optIsland.get();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        boolean onUnlockedIsland = island.isChunkWithinIsland(chunkX, chunkZ);

        lastCheckWasAreaLocked = !onUnlockedIsland;
        lastCheckWasPublicArea = false;
        if (!onUnlockedIsland) {
            lastAreaLockedIsland = island;
        }
        return onUnlockedIsland
                ? PermissionCheckResult.onIsland(player, island)
                : PermissionCheckResult.lockedArea(player, island);
    }

    /** 离线玩家解析 —— 仅做世界判断与岛屿定位,无法判定 bypass(已在调用方过滤) */
    private PermissionCheckResult resolveOffline(Location location, UUID uuid) {
        String worldName = location.getWorld().getName();
        if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(worldName)) {
            boolean inPublicWorld = StarMSkyblock.getInstance().getWorldManager().isPublicWorld(worldName);
            lastCheckWasAreaLocked = false;
            lastCheckWasPublicArea = inPublicWorld;
            return PermissionCheckResult.outsideSkyblockWorld(uuid, inPublicWorld);
        }
        Optional<Island> optIsland = getPlayerCurrentIslandMaxRange(islandManager, location);
        if (optIsland.isEmpty()) {
            lastCheckWasAreaLocked = false;
            lastCheckWasPublicArea = true;
            return PermissionCheckResult.publicArea(uuid);
        }
        Island island = optIsland.get();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        boolean onUnlockedIsland = island.isChunkWithinIsland(chunkX, chunkZ);
        lastCheckWasAreaLocked = !onUnlockedIsland;
        lastCheckWasPublicArea = false;
        if (!onUnlockedIsland) {
            lastAreaLockedIsland = island;
        }
        return onUnlockedIsland
                ? PermissionCheckResult.onIsland(uuid, island)
                : PermissionCheckResult.lockedArea(uuid, island);
    }

    /**
     * 基于 {@link PermissionCheckResult} 执行具体权限判定 —— 内部不再访问 Location/Bukkit,
     * 因此同一事件内多次 check 复用同一 result 时只付出 O(1) 哈希/位运算开销。
     */
    public boolean check(PermissionCheckResult r, IslandPermission permission) {
        if (r.bypass()) return true;
        if (r.outsideSkyblockWorld()) return true;
        if (r.island() == null) {
            // 公共区域(无岛屿归属 或 公共世界)
            return !publicAreaConfig.isEnabled() || publicAreaConfig.getPermission(permission);
        }
        if (!r.onUnlockedIsland()) {
            // 锁定区域(岛屿范围内但未解锁半径)
            return !lockedAreaConfig.isEnabled() || lockedAreaConfig.getPermission(permission);
        }
        return r.island().hasPermission(r.uuid(), permission);
    }

    /**
     * 权限消息提示（带冷却控制，防止刷屏）
     */
    protected void sendDenyMessage(Player player, IslandPermission permission) {
        long now = System.currentTimeMillis();
        long lastTime = lastDenyMessageTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastTime < configManager.getPermissionMessageCooldown()) {
            return;
        }
        lastDenyMessageTime.put(player.getUniqueId(), now);

        if (lastCheckWasAreaLocked) {
            if (lastAreaLockedIsland != null && lastAreaLockedIsland.getOwnerId().equals(player.getUniqueId())) {
                MessageUtil.sendMessage(player, "&e岛屿保护 &f|&c 当前区域未解锁，请升级岛屿！");
            } else if (lastAreaLockedIsland != null) {
                String islandName = lastAreaLockedIsland.getName().isEmpty()
                        ? "岛屿 #" + lastAreaLockedIsland.getId()
                        : lastAreaLockedIsland.getName();
                MessageUtil.sendMessage(player, String.format(
                        "&e岛屿保护 &f|&c 当前区域为 %s 的未解锁区域，没有权限进行操作！", islandName));
            }
            return;
        }

        if (lastCheckWasPublicArea) {
            MessageUtil.sendMessage(player, "&e岛屿保护 &f|&c 公共区域不允许进行操作！");
            return;
        }

        MessageUtil.sendMessage(player, String.format("&e岛屿保护 &f|&c 你没有&e %s &c权限！", permission.getDisplayName()));
    }

    public Optional<Island> getPlayerCurrentIsland(Location location) {
        return getPlayerCurrentIsland(islandManager, location);
    }

    public Optional<Island> getPlayerCurrentIslandMaxRange(Location location) {
        return getPlayerCurrentIslandMaxRange(islandManager, location);
    }
}
