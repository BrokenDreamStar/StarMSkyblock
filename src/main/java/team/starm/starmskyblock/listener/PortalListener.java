package team.starm.starmskyblock.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 传送门监听器 -- 处理空岛世界中下界传送门和末地传送门的跨世界传送逻辑。
 * <p>
 * 核心功能：
 * <ul>
 *   <li>下界传送门：在主世界/下界之间的 1:8 坐标换算；首次进入下界传送到岛内出生点</li>
 *   <li>末地传送门：主世界/下界 -> 末地，以及末地返回主世界</li>
 *   <li>实体传送门：自动定位实体所属岛屿并跨世界传送，目标超出范围则拦截</li>
 *   <li>安全检测：目标位置若不在岛屿已解锁区域内则取消传送并提示</li>
 * </ul>
 */
public class PortalListener implements Listener {

    private final ConfigManager configManager;
    private final SkyblockWorldManager worldManager;
    private final IslandManager islandManager;
    private final PlayerRepository playerRepo;
    /** 记录每个实体最近一次踏入传送门的时间戳，用于 2 秒内去重，避免 EntityPortalEnterEvent 高频触发重复传送 */
    private final Map<UUID, Long> lastPortalEnter = new HashMap<>();

    public PortalListener(ConfigManager configManager, SkyblockWorldManager worldManager, IslandManager islandManager, PlayerRepository playerRepo) {
        this.configManager = configManager;
        this.worldManager = worldManager;
        this.islandManager = islandManager;
        this.playerRepo = playerRepo;
    }

    /** 玩家退出时清理其传送门进入时间戳缓存，避免随独特玩家数无界增长 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastPortalEnter.remove(event.getPlayer().getUniqueId());
    }

    /** 玩家进入传送门时根据传送门类型分发到对应的处理方法 */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        World fromWorld = from.getWorld();
        if (fromWorld == null) return;

        TeleportCause cause = event.getCause();

        if (cause == TeleportCause.NETHER_PORTAL) {
            handleNetherPortal(event, player, fromWorld);
        } else if (cause == TeleportCause.END_PORTAL) {
            handleEndPortal(event, player, fromWorld);
        }
    }

    /** 非玩家实体通过传送门时：定位所属岛屿 -> 跨世界传送 -> 超出范围则拦截并通知岛主 */
    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player) return;
        Location from = event.getFrom();
        World fromWorld = from.getWorld();
        if (fromWorld == null) return;

        String worldName = fromWorld.getName();

        boolean fromNormal = worldManager.isNormalWorld(worldName);
        boolean fromNether = worldManager.isNetherWorld(worldName);
        boolean fromEnd = worldManager.isEndWorld(worldName);

        if (!fromNormal && !fromNether && !fromEnd) return;


        // 查找实体所在区块属于哪个岛屿（位运算取区块坐标，避免同步加载区块）
        int chunkX = from.getBlockX() >> 4;
        int chunkZ = from.getBlockZ() >> 4;
        Optional<Island> optionalIsland = islandManager.getIslandAt(chunkX, chunkZ);
        if (optionalIsland.isEmpty()) {
            optionalIsland = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
        }
        if (optionalIsland.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        Island island = optionalIsland.get();

        // 处理末地传送门：主世界END_PORTAL -> 末地岛屿传送点，末地 -> 主世界岛屿传送点
        if (fromEnd || ((fromNormal || fromNether) && from.getBlock().getType() == Material.END_PORTAL)) {
            World targetWorld = fromEnd
                    ? worldManager.getOrCreateSkyblockWorld()
                    : worldManager.getOrCreateSkyblockEnd();
            if (targetWorld == null) {
                event.setCancelled(true);
                return;
            }
            event.setTo(getIslandLocation(island, targetWorld));
            return;
        }

        // 下界未解锁时阻止非玩家实体进入
        if (fromNormal && !island.isNetherUnlocked()) {
            event.setCancelled(true);
            return;
        }

        World targetWorld = fromNormal
                ? worldManager.getOrCreateSkyblockNether()
                : worldManager.getOrCreateSkyblockWorld();
        if (targetWorld == null) {
            event.setCancelled(true);
            return;
        }

        Location targetLoc = fromNormal
                ? calculateNetherPortalLocation(from, targetWorld, island)
                : calculateOverworldPortalLocation(from, targetWorld, island);

        String boundsKey = checkIslandBounds(targetLoc, island);
        if (boundsKey != null) {
            event.setCancelled(true);
            notifyIslandMembers(island, from, targetLoc);
            return;
        }

        event.setTo(targetLoc);
    }

    /** 通知岛屿所有成员和岛主：实体试图创建传送门但目标位置不可达 */
    private void notifyIslandMembers(Island island, Location portalLoc, Location targetLoc) {
        int chunkX = targetLoc.getBlockX() >> 4;
        int chunkZ = targetLoc.getBlockZ() >> 4;
        String reason = MessageUtil.format(island.isChunkWithinMaxRange(chunkX, chunkZ)
                ? "island.portal.reason-locked" : "island.portal.reason-out");
        String msg = MessageUtil.format("island.portal.entity-portal-blocked",
                Map.of("x", portalLoc.getBlockX(), "y", portalLoc.getBlockY(), "z", portalLoc.getBlockZ(), "reason", reason));
        island.getMembers().keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                MessageUtil.sendMessage(p, msg);
            }
        });
        Player owner = Bukkit.getPlayer(island.getOwnerId());
        if (owner != null && owner.isOnline()) {
            MessageUtil.sendMessage(owner, msg);
        }
    }

    // ==================== 下界传送门 ====================

    /** 处理玩家下界传送门交互：根据来源世界分发到"进入下界"或"离开下界" */
    private void handleNetherPortal(PlayerPortalEvent event, Player player, World fromWorld) {
        String worldName = fromWorld.getName();
        boolean fromNormal = worldManager.isNormalWorld(worldName);
        boolean fromNether = worldManager.isNetherWorld(worldName);
        if (!fromNormal && !fromNether) return;

        // 1. 先按传送门所在区块位置查找岛屿（位运算取区块坐标，避免同步加载区块）
        int chunkX = event.getFrom().getBlockX() >> 4;
        int chunkZ = event.getFrom().getBlockZ() >> 4;
        Optional<Island> optionalIsland = islandManager.getIslandAt(chunkX, chunkZ);
        if (optionalIsland.isEmpty()) {
            optionalIsland = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
        }

        Island island = null;
        if (optionalIsland.isPresent()) {
            island = optionalIsland.get();
            // 非成员从主世界进入下界：检查岛屿是否解锁下界
            // 岛屿成员可通过以触发自动解锁(handleToNether 中的 tryUnlockNether)
            if (fromNormal && !island.isNetherUnlocked()) {
                IslandPermissionLevel role = island.getMemberRole(player.getUniqueId());
                boolean isMemberOrOwner = role.getPermissionLevel() >= IslandPermissionLevel.MEMBER.getPermissionLevel();
                if (!isMemberOrOwner) {
                    MessageUtil.send(player, "island.portal.nether-not-unlocked");
                    event.setCancelled(true);
                    return;
                }
            }
        } else {
            // 2. 回退：传送门不在任何岛屿范围时，按玩家关联查找
            optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
            if (optionalIsland.isPresent()) {
                island = optionalIsland.get();
            } else {
                return;
            }
        }

        if (fromNormal) {
            handleToNether(event, player, island);
        } else if (fromNether) {
            handleFromNether(event, player, island);
        }
    }

    /** 处理从主世界进入下界：首次进入传送岛内下界出生点，后续按坐标换算 */
    private void handleToNether(PlayerPortalEvent event, Player player, Island island) {
        World targetWorld = worldManager.getOrCreateSkyblockNether();
        if (targetWorld == null) return;

        boolean isMember = island.getMemberRole(player.getUniqueId()).getPermissionLevel()
                >= IslandPermissionLevel.MEMBER.getPermissionLevel();

        // 首次进入下界（仅岛屿成员）：取消传送门事件并直接传送到岛屿下界出生点
        if (isMember && playerRepo.isFirstNetherJoin(player.getUniqueId())) {
            Location spawnLoc = getIslandLocation(island, targetWorld);
            event.setCancelled(true);
            playerRepo.setFirstNetherJoin(player.getUniqueId(), false);
            MessageUtil.send(player, "island.portal.first-join-nether");
            tryUnlockNether(island);
            player.teleport(spawnLoc);
            return;
        }

        Location targetLoc = calculateNetherPortalLocation(event.getFrom(), targetWorld, island);
        String boundsKey = checkIslandBounds(targetLoc, island);
        if (boundsKey != null) {
            MessageUtil.send(player, boundsKey);
            event.setCancelled(true);
            return;
        }
        tryUnlockNether(island);
        event.setTo(targetLoc);
    }

    /** 处理从下界返回主世界 */
    private void handleFromNether(PlayerPortalEvent event, Player player, Island island) {
        World targetWorld = worldManager.getOrCreateSkyblockWorld();
        if (targetWorld == null) return;

        Location targetLoc = calculateOverworldPortalLocation(event.getFrom(), targetWorld, island);
        String boundsKey = checkIslandBounds(targetLoc, island);
        if (boundsKey != null) {
            MessageUtil.send(player, boundsKey);
            event.setCancelled(true);
            return;
        }
        event.setTo(targetLoc);
    }

    /**
     * 计算主世界 -> 下界的传送门目标位置。
     * 基于岛屿中心坐标的相对偏移进行 1:8 缩放，而非绝对坐标缩放，
     * 以避免因岛屿远离世界原点导致目标位置超出岛屿范围。
     */
    private Location calculateNetherPortalLocation(Location from, World targetWorld, Island island) {
        return scalePortalLocation(from, targetWorld, island, 1.0 / 8.0);
    }

    /**
     * 计算下界 -> 主世界的传送门目标位置。
     * 基于岛屿中心坐标的相对偏移进行 8:1 缩放。
     */
    private Location calculateOverworldPortalLocation(Location from, World targetWorld, Island island) {
        return scalePortalLocation(from, targetWorld, island, 8.0);
    }

    /** 计算岛屿中心方块坐标（centerChunk * 16 + 8），供传送门缩放与传送点计算共用，消除重复。 */
    private double[] getIslandCenterBlockXZ(Island island) {
        return new double[]{
                island.getCenterChunkX() * 16.0 + 8.0,
                island.getCenterChunkZ() * 16.0 + 8.0
        };
    }

    /**
     * 基于岛屿中心坐标的相对偏移进行缩放，计算传送门目标位置。
     * <p>下界 1:8 取 {@code 1.0/8.0}，主世界 8:1 取 {@code 8.0}。相对偏移缩放（而非绝对坐标）
     * 避免岛屿远离世界原点时目标位置超出岛屿范围。</p>
     */
    private Location scalePortalLocation(Location from, World targetWorld, Island island, double factor) {
        double[] center = getIslandCenterBlockXZ(island);
        double offsetX = from.getX() - center[0];
        double offsetZ = from.getZ() - center[1];
        return new Location(targetWorld,
                center[0] + offsetX * factor,
                from.getY(),
                center[1] + offsetZ * factor,
                from.getYaw(), from.getPitch());
    }

    /**
     * 检查目标位置是否在岛屿已解锁区域内。
     *
     * @return null 如果目标在岛屿已解锁区域内；否则返回对应的提示消息键
     */
    private String checkIslandBounds(Location location, Island island) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (island.isChunkWithinIsland(chunkX, chunkZ)) {
            return null;
        }
        if (island.isChunkWithinMaxRange(chunkX, chunkZ)) {
            return "island.portal.bounds-locked";
        }
        return "island.portal.bounds-out";
    }

    /** 尝试解锁岛屿下界（仅当岛屿尚未解锁且进入者是岛屿成员时） */
    private void tryUnlockNether(Island island) {
        if (!island.isNetherUnlocked()) {
            island.setNetherUnlocked(true);
            islandManager.updateIslandNetherUnlocked(island.getId(), true);
        }
    }

    // ==================== 末地传送门 ====================

    /** 处理玩家末地传送门：主世界/下界 -> 末地岛屿位置，末地 -> 主世界 spawn */
    private void handleEndPortal(PlayerPortalEvent event, Player player, World fromWorld) {
        String worldName = fromWorld.getName();

        Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());

        World targetWorld = null;
        Location targetLoc;

        if (worldManager.isNormalWorld(worldName) || worldManager.isNetherWorld(worldName)) {
            targetWorld = worldManager.getOrCreateSkyblockEnd();
        } else if (worldManager.isEndWorld(worldName)) {
            targetWorld = worldManager.getOrCreateSkyblockWorld();
        }

        if (targetWorld != null) {
            if (optionalIsland.isPresent()) {
                targetLoc = getIslandLocation(optionalIsland.get(), targetWorld);
            } else {
                if (worldManager.isEndWorld(worldName)) {
                    targetLoc = targetWorld.getSpawnLocation();
                } else {
                    targetLoc = new Location(targetWorld, 100, 50, 0);
                }
            }
            event.setTo(targetLoc);
            event.setCancelled(true);
            player.teleport(targetLoc);
        }
    }

    /**
     * 监听玩家踏入传送门的瞬间（EntityPortalEnterEvent）。
     * Paper 在末地维度中不会触发 PlayerPortalEvent，
     * 但一定会触发 EntityPortalEnterEvent -- 这是修复末地->主世界传送的关键。
     */
    @EventHandler
    public void onEntityPortalEnter(EntityPortalEnterEvent event) {
        Entity entity = event.getEntity();
        Location location = event.getLocation();
        World world = location.getWorld();
        if (world == null) return;

        String worldName = world.getName();
        boolean isEndWorld = worldManager.isEndWorld(worldName);
        boolean isNormalWorld = worldManager.isNormalWorld(worldName);
        boolean isNetherWorld = worldManager.isNetherWorld(worldName);
        if (!isEndWorld && !isNormalWorld && !isNetherWorld) return;

        Material blockType = location.getBlock().getType();
        if (blockType != Material.END_PORTAL && blockType != Material.NETHER_PORTAL) return;

        UUID uuid = entity.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastPortalEnter.getOrDefault(uuid, 0L) < 2000) return;
        lastPortalEnter.put(uuid, now);

        if (entity instanceof Player player) {
            handlePlayerPortalEnter(player, world, isEndWorld);
        } else {
            handleEntityPortalEnter(entity, world, location, isEndWorld);
        }
    }

    /** 玩家从末地踏入传送门时传送回主世界岛屿传送点（Paper 在末地不触发 PlayerPortalEvent 的兜底） */
    private void handlePlayerPortalEnter(Player player, World world, boolean isEndWorld) {
        if (!isEndWorld) return;

        World targetWorld = worldManager.getOrCreateSkyblockWorld();
        if (targetWorld == null) return;

        Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
        Location targetLoc = optionalIsland.isPresent()
                ? getIslandLocation(optionalIsland.get(), targetWorld)
                : targetWorld.getSpawnLocation();

        player.setPortalCooldown(40);
        player.teleport(targetLoc);

        Bukkit.getScheduler().runTask(StarMSkyblock.getInstance(), () -> {
            World current = player.getWorld();
            if (current == null || !current.equals(targetWorld)) {
                player.teleport(targetLoc);
            }
        });
    }

    /**
     * 非玩家实体从末地踏入传送门时，定位其所属岛屿并传送到主世界对应岛屿位置。
     * <p>仅处理末地场景：下界传送门已由 {@link #onEntityPortal} 通过 setTo 处理，
     * 此处补齐 Paper 在末地维度不触发 EntityPortalEvent 的缺口。</p>
     */
    private void handleEntityPortalEnter(Entity entity, World world, Location location,
                                          boolean isEndWorld) {
        // 仅处理末地传送门 -- 因为 Paper 在末地维度不触发 EntityPortalEvent，
        // 但 EntityPortalEnterEvent 一定会触发。
        // 非玩家实体的下界传送门由 onEntityPortal(EntityPortalEvent) 通过 setTo 处理。
        if (!isEndWorld) return;

        // Find island by chunk position (位运算取区块坐标，避免同步加载区块)
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        Optional<Island> optionalIsland = islandManager.getIslandAt(chunkX, chunkZ);
        if (optionalIsland.isEmpty()) {
            optionalIsland = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
        }
        if (optionalIsland.isEmpty()) return;

        Island island = optionalIsland.get();

        World targetWorld = isEndWorld
                ? worldManager.getOrCreateSkyblockWorld()
                : worldManager.getOrCreateSkyblockEnd();
        if (targetWorld == null) return;
        entity.teleport(getIslandLocation(island, targetWorld));
    }

    /** 计算岛屿在该世界的传送点坐标（中心区块 + 配置偏移量） */
    private Location getIslandLocation(Island island, World world) {
        double[] center = getIslandCenterBlockXZ(island);
        int islandHeight = configManager.getIslandHeight();

        double[] offsets = getTeleportOffsetsByWorldType(world, island);

        double teleportX = center[0] + offsets[0];
        double teleportY = islandHeight + offsets[1];
        double teleportZ = center[1] + offsets[2];

        return new Location(world, teleportX, teleportY, teleportZ, (float) offsets[3], (float) offsets[4]);
    }

    /** 根据世界类型和岛屿结构 ID 获取传送点偏移量 */
    private double[] getTeleportOffsetsByWorldType(World world, Island island) {
        String worldName = world.getName();

        Island.WorldType worldType;
        if (worldManager.isNetherWorld(worldName)) {
            worldType = Island.WorldType.NETHER;
        } else if (worldManager.isEndWorld(worldName)) {
            worldType = Island.WorldType.END;
        } else {
            worldType = Island.WorldType.NORMAL;
        }

        String schematicId = (island != null) ? island.getSchematicId() : null;
        return configManager.getTeleportOffsetsBySchematicAndWorldType(schematicId, worldType);
    }
}
