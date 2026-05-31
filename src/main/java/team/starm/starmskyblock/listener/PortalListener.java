package team.starm.starmskyblock.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.database.PlayerRepository;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Optional;

/**
 * 传送门监听器 —— 处理空岛世界中下界传送门和末地传送门的跨世界传送逻辑。
 * <p>
 * 核心功能：
 * <ul>
 *   <li>下界传送门：在主世界/下界之间的 1:8 坐标换算；首次进入下界传送到岛内出生点</li>
 *   <li>末地传送门：主世界/下界 → 末地，以及末地返回主世界</li>
 *   <li>实体传送门：自动定位实体所属岛屿并跨世界传送，目标超出范围则拦截</li>
 *   <li>安全检测：目标位置若不在岛屿已解锁区域内则取消传送并提示</li>
 * </ul>
 */
public class PortalListener implements Listener {

    private final ConfigManager configManager;
    private final SkyblockWorldManager worldManager;
    private final IslandManager islandManager;
    private final PlayerRepository playerRepo;

    public PortalListener(ConfigManager configManager, SkyblockWorldManager worldManager, IslandManager islandManager, PlayerRepository playerRepo) {
        this.configManager = configManager;
        this.worldManager = worldManager;
        this.islandManager = islandManager;
        this.playerRepo = playerRepo;
    }

    /** 玩家进入传送门时根据传送门类型分发到对应的处理方法 */
    @EventHandler
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

    /** 非玩家实体通过传送门时：定位所属岛屿 → 跨世界传送 → 超出范围则拦截并通知岛主 */
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

        if (!fromNormal && !fromNether) return;

        // 查找实体所在区块属于哪个岛屿
        int chunkX = from.getChunk().getX();
        int chunkZ = from.getChunk().getZ();
        Optional<Island> optionalIsland = islandManager.getIslandAt(chunkX, chunkZ);
        if (optionalIsland.isEmpty()) {
            optionalIsland = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
        }
        if (optionalIsland.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        Island island = optionalIsland.get();

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

        Location targetLoc = event.getTo();
        if (targetLoc == null) {
            targetLoc = fromNormal
                    ? calculateNetherPortalLocation(from, targetWorld)
                    : calculateOverworldPortalLocation(from, targetWorld);
        } else {
            targetLoc = new Location(targetWorld, targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(),
                    targetLoc.getYaw(), targetLoc.getPitch());
        }

        String boundsMsg = checkIslandBounds(targetLoc, island);
        if (boundsMsg != null) {
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
        String reason = island.isChunkWithinMaxRange(chunkX, chunkZ) ? "区域未解锁" : "超出岛屿范围";
        String msg = "§c[岛屿] 有实体在 (" + portalLoc.getBlockX() + ", " + portalLoc.getBlockY() + ", " + portalLoc.getBlockZ() + ") 尝试创建下界传送门但" + reason + "！";
        island.getMembers().keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        });
        Player owner = Bukkit.getPlayer(island.getOwnerId());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(msg);
        }
    }

    // ==================== 下界传送门 ====================

    /** 处理玩家下界传送门交互：根据来源世界分发到"进入下界"或"离开下界" */
    private void handleNetherPortal(PlayerPortalEvent event, Player player, World fromWorld) {
        String worldName = fromWorld.getName();
        boolean fromNormal = worldManager.isNormalWorld(worldName);
        boolean fromNether = worldManager.isNetherWorld(worldName);
        if (!fromNormal && !fromNether) return;

        // 1. 先按玩家关联查找（owner / member）
        Optional<Island> optionalIsland = islandManager.getIslandByPlayer(player.getUniqueId());
        Island island = null;

        if (optionalIsland.isPresent()) {
            island = optionalIsland.get();
        } else {
            // 2. 合作者/访客：按区块位置查找
            int chunkX = event.getFrom().getChunk().getX();
            int chunkZ = event.getFrom().getChunk().getZ();
            optionalIsland = islandManager.getIslandAt(chunkX, chunkZ);
            if (optionalIsland.isEmpty()) {
                optionalIsland = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
            }
            if (optionalIsland.isEmpty()) return;

            island = optionalIsland.get();

            // 3. 非成员从主世界进入下界：检查岛屿是否解锁下界
            if (fromNormal && !island.isNetherUnlocked()) {
                player.sendMessage("§c该岛屿尚未解锁下界，无法进入！");
                event.setCancelled(true);
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

        // 首次进入下界（仅岛屿成员）：传送到岛屿下界出生点
        if (isMember && playerRepo.isFirstNetherJoin(player.getUniqueId())) {
            Location spawnLoc = getIslandLocation(island, targetWorld);
            event.setTo(spawnLoc);
            playerRepo.setFirstNetherJoin(player.getUniqueId(), false);
            player.sendMessage("§a欢迎来到下界！这是你第一次进入，已将你传送到岛屿下界出生点。");
            tryUnlockNether(island);
            return;
        }

        Location targetLoc = event.getTo();
        if (targetLoc == null) {
            targetLoc = calculateNetherPortalLocation(event.getFrom(), targetWorld);
        } else {
            targetLoc = new Location(targetWorld, targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(),
                    targetLoc.getYaw(), targetLoc.getPitch());
        }
        String msg = checkIslandBounds(targetLoc, island);
        if (msg != null) {
            player.sendMessage(msg);
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

        Location targetLoc = event.getTo();
        if (targetLoc == null) {
            targetLoc = calculateOverworldPortalLocation(event.getFrom(), targetWorld);
        } else {
            targetLoc = new Location(targetWorld, targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(),
                    targetLoc.getYaw(), targetLoc.getPitch());
        }
        String msg = checkIslandBounds(targetLoc, island);
        if (msg != null) {
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }
        event.setTo(targetLoc);
    }

    /** 下界坐标 → 主世界坐标的 1:8 换算 */
    private Location calculateNetherPortalLocation(Location from, World targetWorld) {
        return new Location(targetWorld, from.getX() / 8.0, from.getY(), from.getZ() / 8.0,
                from.getYaw(), from.getPitch());
    }

    /** 主世界坐标 → 下界坐标的 8:1 换算 */
    private Location calculateOverworldPortalLocation(Location from, World targetWorld) {
        return new Location(targetWorld, from.getX() * 8.0, from.getY(), from.getZ() * 8.0,
                from.getYaw(), from.getPitch());
    }

    /**
     * 检查目标位置是否在岛屿已解锁区域内。
     *
     * @return null 如果目标在岛屿已解锁区域内；否则返回对应的提示消息
     */
    private String checkIslandBounds(Location location, Island island) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (island.isChunkWithinIsland(chunkX, chunkZ)) {
            return null;
        }
        if (island.isChunkWithinMaxRange(chunkX, chunkZ)) {
            return "§c无法创建传送门：目标位置岛屿区域未解锁！";
        }
        return "§c无法创建传送门：目标位置超出你的岛屿范围！";
    }

    /** 尝试解锁岛屿下界（仅当岛屿尚未解锁且进入者是岛屿成员时） */
    private void tryUnlockNether(Island island) {
        if (!island.isNetherUnlocked()) {
            island.setNetherUnlocked(true);
            islandManager.updateIslandNetherUnlocked(island.getId(), true);
        }
    }

    // ==================== 末地传送门 ====================

    /** 处理玩家末地传送门：主世界/下界 → 末地岛屿位置，末地 → 主世界 spawn */
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
        }
    }

    /** 计算岛屿在该世界的传送点坐标（中心区块 + 配置偏移量） */
    private Location getIslandLocation(Island island, World world) {
        int startX = island.getCenterChunkX() * 16;
        int startZ = island.getCenterChunkZ() * 16;
        int islandHeight = configManager.getIslandHeight();

        double[] offsets = getTeleportOffsetsByWorldType(world, island);

        double teleportX = startX + 8 + offsets[0];
        double teleportY = islandHeight + offsets[1];
        double teleportZ = startZ + 8 + offsets[2];

        return new Location(world, teleportX, teleportY, teleportZ);
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
