package team.starm.starmskyblock.permission;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
    /** 记录每个玩家最近一次收到权限拒绝消息的时间戳，用于消息冷却防刷屏。
     *  synchronizedMap 包裹保证多线程访问安全（accessOrder=true 提供 LRU 淘汰，上限 256 条）。 */
    protected final Map<UUID, Long> lastDenyMessageTime = Collections.synchronizedMap(
            new LinkedHashMap<UUID, Long>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Long> eldest) {
                    return size() > 256;
                }
            });
    /** 每个玩家最近一次权限解析结果，供 {@link #sendDenyMessage} 读取拒绝原因。
     *  <p>原实现把"是否锁定区域/公共区域/锁定岛屿"存在实例布尔字段上，12 个子管理器各为全局单例，
     *  跨玩家复用同一组字段会导致玩家 A 的检查状态污染玩家 B 的拒绝消息。改为按 UUID 存储解析结果，
     *  每个玩家读自己的 {@link PermissionCheckResult}，从根上消除跨玩家串扰。 */
    protected final Map<UUID, PermissionCheckResult> lastCheckResult = Collections.synchronizedMap(
            new LinkedHashMap<UUID, PermissionCheckResult>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, PermissionCheckResult> eldest) {
                    return size() > 512;
                }
            });

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
        World world = location.getWorld();
        if (world == null) {
            // 位置无关联世界（已卸载或非法构造），视为空岛世界之外，不阻断
            PermissionCheckResult r = PermissionCheckResult.outsideSkyblockWorld(player, false);
            lastCheckResult.put(player.getUniqueId(), r);
            return r;
        }
        String worldName = world.getName();
        if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(worldName)) {
            boolean inPublicWorld = StarMSkyblock.getInstance().getWorldManager().isPublicWorld(worldName);
            PermissionCheckResult r = PermissionCheckResult.outsideSkyblockWorld(player, inPublicWorld);
            lastCheckResult.put(player.getUniqueId(), r);
            return r;
        }

        Optional<Island> optIsland = getPlayerCurrentIslandMaxRange(islandManager, location);

        if (optIsland.isEmpty()) {
            PermissionCheckResult r = PermissionCheckResult.publicArea(player);
            lastCheckResult.put(player.getUniqueId(), r);
            return r;
        }

        Island island = optIsland.get();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        boolean onUnlockedIsland = island.isChunkWithinIsland(chunkX, chunkZ);
        PermissionCheckResult r = onUnlockedIsland
                ? PermissionCheckResult.onIsland(player, island)
                : PermissionCheckResult.lockedArea(player, island);
        lastCheckResult.put(player.getUniqueId(), r);
        return r;
    }

    /** 离线玩家解析 —— 仅做世界判断与岛屿定位,无法判定 bypass(已在调用方过滤) */
    private PermissionCheckResult resolveOffline(Location location, UUID uuid) {
        World world = location.getWorld();
        if (world == null) {
            PermissionCheckResult r = PermissionCheckResult.outsideSkyblockWorld(uuid, false);
            lastCheckResult.put(uuid, r);
            return r;
        }
        String worldName = world.getName();
        if (!StarMSkyblock.getInstance().getWorldManager().isSkyblockWorldName(worldName)) {
            boolean inPublicWorld = StarMSkyblock.getInstance().getWorldManager().isPublicWorld(worldName);
            PermissionCheckResult r = PermissionCheckResult.outsideSkyblockWorld(uuid, inPublicWorld);
            lastCheckResult.put(uuid, r);
            return r;
        }
        Optional<Island> optIsland = getPlayerCurrentIslandMaxRange(islandManager, location);
        if (optIsland.isEmpty()) {
            PermissionCheckResult r = PermissionCheckResult.publicArea(uuid);
            lastCheckResult.put(uuid, r);
            return r;
        }
        Island island = optIsland.get();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        boolean onUnlockedIsland = island.isChunkWithinIsland(chunkX, chunkZ);
        PermissionCheckResult r = onUnlockedIsland
                ? PermissionCheckResult.onIsland(uuid, island)
                : PermissionCheckResult.lockedArea(uuid, island);
        lastCheckResult.put(uuid, r);
        return r;
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
            return publicAreaConfig.getPermission(permission);
        }
        if (!r.onUnlockedIsland()) {
            // 锁定区域(岛屿范围内但未解锁半径)
            return lockedAreaConfig.getPermission(permission);
        }
        return r.island().hasPermission(r.uuid(), permission);
    }

    /**
     * 权限消息提示（带冷却控制，防止刷屏）
     * <p>拒绝原因从该玩家最近一次 {@link PermissionCheckResult} 派生，而非实例字段，避免跨玩家串扰：
     * <ul>
     *   <li>锁定区域：岛屿存在但不在已解锁半径内（{@code island != null && !onUnlockedIsland}）</li>
     *   <li>公共区域：无岛屿归属且处于公共世界（{@code island == null && inPublicWorld}）</li>
     *   <li>其他：默认无权限提示</li>
     * </ul>
     */
    protected void sendDenyMessage(Player player, IslandPermission permission) {
        long now = System.currentTimeMillis();
        long lastTime = lastDenyMessageTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastTime < configManager.getPermissionMessageCooldown()) {
            return;
        }
        lastDenyMessageTime.put(player.getUniqueId(), now);

        PermissionCheckResult r = lastCheckResult.get(player.getUniqueId());
        boolean areaLocked = r != null && r.island() != null && !r.onUnlockedIsland();
        boolean publicArea = r != null && r.island() == null && r.inPublicWorld();

        if (areaLocked) {
            Island lockedIsland = r.island();
            if (lockedIsland.getOwnerId().equals(player.getUniqueId())) {
                MessageUtil.send(player, "protection.locked-area");
            } else {
                String islandName = lockedIsland.getName().isEmpty()
                        ? "岛屿 #" + lockedIsland.getId()
                        : lockedIsland.getName();
                MessageUtil.send(player, "protection.locked-area-other", Map.of("island", islandName));
            }
            return;
        }

        if (publicArea) {
            MessageUtil.send(player, "protection.public-area");
            return;
        }

        MessageUtil.send(player, "protection.no-permission", Map.of("permission", permission.getDisplayName()));
    }

    public Optional<Island> getPlayerCurrentIsland(Location location) {
        return getPlayerCurrentIsland(islandManager, location);
    }

    public Optional<Island> getPlayerCurrentIslandMaxRange(Location location) {
        return getPlayerCurrentIslandMaxRange(islandManager, location);
    }
}
