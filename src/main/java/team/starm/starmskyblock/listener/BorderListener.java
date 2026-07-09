package team.starm.starmskyblock.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import team.starm.starmskyblock.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.util.reflection.WorldBorderReflection;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 岛屿边界监听器 —— 为每个玩家动态创建并更新 WorldBorder，使其视觉上限定在岛屿范围内。
 * <p>
 * 监听玩家移动、传送和世界切换事件，根据玩家所在区块实时计算并设置对应的岛屿边界。
 * 边界显示可由玩家通过指令开关，偏好存入数据库。
 * 玩家不在空岛世界或未找到对应岛屿时清除边界（显示默认无限边界）。
 */
public class BorderListener implements Listener {

    private final IslandManager islandManager;
    private final SkyblockWorldManager worldManager;
    private final PlayerRepository playerRepo;
    private final ConfigManager configManager;
    /**
     * 玩家边界显示开关缓存(容量无上限 —— 玩家总量有界,且 {@link #setPlayerShowBorder} 在玩家指令中触发,
     * 频率极低;PlayerMove 热路径只读)。改用 {@link ConcurrentHashMap} 避免 {@code accessOrder=true} 的
     * LinkedHashMap 在并发 {@code getOrDefault} 时破坏链表结构。
     */
    private final Map<UUID, Boolean> borderCache = new ConcurrentHashMap<>();
    /** 岛屿边界缓存：islandId → CachedBorder，避免每次跨区块移动都创建 WorldBorder 对象 */
    private final Map<Integer, CachedBorder> islandBorderCache = new ConcurrentHashMap<>();

    public BorderListener(IslandManager islandManager, SkyblockWorldManager worldManager, PlayerRepository playerRepo, ConfigManager configManager) {
        this.islandManager = islandManager;
        this.worldManager = worldManager;
        this.playerRepo = playerRepo;
        this.configManager = configManager;
    }

    /** 查询某玩家是否开启了岛屿边界显示（默认由 config.yml 中 show-border-default 控制） */
    public boolean isPlayerShowBorder(UUID playerUuid) {
        return borderCache.getOrDefault(playerUuid, configManager.isShowBorderDefault());
    }

    /** 设置玩家边界显示开关（更新内存缓存 + 数据库） */
    public void setPlayerShowBorder(UUID playerUuid, boolean show) {
        borderCache.put(playerUuid, show);
        playerRepo.setBorderEnabled(playerUuid, show);
    }

    /** 玩家加入服务器时加载其边界偏好并更新边界 */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 先查询数据库中的已保存偏好（玩家无记录时返回 empty，此时使用配置默认值）
        Optional<Boolean> savedBorder = playerRepo.getBorderEnabled(uuid);
        boolean showBorder = savedBorder.orElseGet(() -> configManager.isShowBorderDefault());

        // 保存/更新玩家名（新玩家会 INSERT 一行，border_enabled 使用 DEFAULT 0）
        playerRepo.savePlayerName(uuid, player.getName());

        // 新玩家：将配置默认值持久化到数据库
        if (savedBorder.isEmpty()) {
            playerRepo.setBorderEnabled(uuid, showBorder);
        }

        borderCache.put(uuid, showBorder);
        updatePlayerBorder(player);
    }

    /** 玩家退出时清理其边界开关缓存，避免随独特玩家数无界增长（下次加入时由 onPlayerJoin 重新加载） */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        borderCache.remove(event.getPlayer().getUniqueId());
    }

    /** 玩家传送后更新边界 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }
        updatePlayerBorder(event.getPlayer(), event.getTo());
    }

    /** 玩家切换世界后更新边界 */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        updatePlayerBorder(event.getPlayer());
    }

    /** 玩家移动时如果跨越了区块边界则更新边界 */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        int fromChunkX = from.getBlockX() >> 4;
        int fromChunkZ = from.getBlockZ() >> 4;
        int toChunkX = to.getBlockX() >> 4;
        int toChunkZ = to.getBlockZ() >> 4;

        // 仅在跨越区块边界时更新，减少不必要的 WorldBorder 调用
        if (fromChunkX == toChunkX && fromChunkZ == toChunkZ) {
            return;
        }

        updatePlayerBorder(event.getPlayer());
    }

    /** 根据玩家当前位置更新其 WorldBorder */
    public void updatePlayerBorder(Player player) {
        updatePlayerBorder(player, player.getLocation());
    }

    /**
     * 根据指定位置更新玩家的 WorldBorder。
     * 逻辑链路：
     * 1. 是否在空岛世界？→ 否：清除边界
     * 2. 玩家是否开启边界显示？→ 否：清除边界
     * 3. 当前位置是否属于某个岛屿？→ 否：清除边界；是：设置为该岛屿的边界
     */
    public void updatePlayerBorder(Player player, Location location) {
        World playerWorld = location.getWorld();
        if (playerWorld == null) {
            player.setWorldBorder(null);
            return;
        }

        boolean isInSkyblockWorld = worldManager.isSkyblockWorld(playerWorld);
        if (!isInSkyblockWorld) {
            WorldBorderReflection.resetWorldBorder(player, playerWorld);
            return;
        }

        if (!isPlayerShowBorder(player.getUniqueId())) {
            WorldBorderReflection.resetWorldBorder(player, playerWorld);
            return;
        }

        Optional<Island> currentIsland = islandManager.getIslandAtMaxRange(
                location.getBlockX() >> 4, location.getBlockZ() >> 4);
        currentIsland.ifPresentOrElse(
                island -> WorldBorderReflection.sendWorldBorder(player, getOrCreateIslandBorder(island)),
                () -> WorldBorderReflection.resetWorldBorder(player, playerWorld));
    }

    /** 根据岛屿参数获取缓存的 WorldBorder（中心 + 边长），半径不变时复用缓存对象 */
    public WorldBorder getOrCreateIslandBorder(Island island) {
        int radius = island.getRadius();
        CachedBorder cached = islandBorderCache.get(island.getId());
        if (cached != null && cached.radius == radius) {
            return cached.border;
        }
        WorldBorder border = createIslandBorder(island, radius);
        islandBorderCache.put(island.getId(), new CachedBorder(radius, border));
        return border;
    }

    private static WorldBorder createIslandBorder(Island island, int radiusChunks) {
        double sideLength = (radiusChunks * 2 + 1) * 16.0;
        double centerX = island.getCenterChunkX() * 16.0 + 8.0;
        double centerZ = island.getCenterChunkZ() * 16.0 + 8.0;
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(sideLength);
        border.setWarningDistance(0);
        return border;
    }

    private record CachedBorder(int radius, WorldBorder border) {}
}
