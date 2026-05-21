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
import team.starm.starmskyblock.database.SQLiteManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Optional;

public class PortalListener implements Listener {

    private final ConfigManager configManager;
    private final SkyblockWorldManager worldManager;
    private final IslandManager islandManager;
    private final SQLiteManager sqliteManager;

    public PortalListener(ConfigManager configManager, SkyblockWorldManager worldManager, IslandManager islandManager, SQLiteManager sqliteManager) {
        this.configManager = configManager;
        this.worldManager = worldManager;
        this.islandManager = islandManager;
        this.sqliteManager = sqliteManager;
    }

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

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;
        Location from = event.getFrom();
        World fromWorld = from.getWorld();
        if (fromWorld == null) return;

        String normalName = configManager.getWorldNameNormal();
        String netherName = configManager.getWorldNameNether();
        String worldName = fromWorld.getName();

        boolean fromNormal = worldName.equals(normalName);
        boolean fromNether = worldName.equals(netherName);

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

    private void handleNetherPortal(PlayerPortalEvent event, Player player, World fromWorld) {
        String normalName = configManager.getWorldNameNormal();
        String netherName = configManager.getWorldNameNether();

        Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());
        if (optionalIsland.isEmpty()) return;

        Island island = optionalIsland.get();

        if (fromWorld.getName().equals(normalName)) {
            handleToNether(event, player, island);
        } else if (fromWorld.getName().equals(netherName)) {
            handleFromNether(event, player, island);
        }
    }

    private void handleToNether(PlayerPortalEvent event, Player player, Island island) {
        World targetWorld = worldManager.getOrCreateSkyblockNether();
        if (targetWorld == null) return;

        // 首次进入下界：传送到岛屿下界出生点
        if (sqliteManager.isFirstNetherJoin(player.getUniqueId())) {
            Location spawnLoc = getIslandLocation(island, targetWorld);
            event.setTo(spawnLoc);
            sqliteManager.setFirstNetherJoin(player.getUniqueId(), false);
            player.sendMessage("§a欢迎来到下界！这是你第一次进入，已将你传送到岛屿下界出生点。");
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
        event.setTo(targetLoc);
    }

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

    private Location calculateNetherPortalLocation(Location from, World targetWorld) {
        return new Location(targetWorld, from.getX() / 8.0, from.getY(), from.getZ() / 8.0,
                from.getYaw(), from.getPitch());
    }

    private Location calculateOverworldPortalLocation(Location from, World targetWorld) {
        return new Location(targetWorld, from.getX() * 8.0, from.getY(), from.getZ() * 8.0,
                from.getYaw(), from.getPitch());
    }

    /**
     * @return null 如果目标在岛屿已解锁区域内，否则返回提示消息
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

    // ==================== 末地传送门 ====================

    private void handleEndPortal(PlayerPortalEvent event, Player player, World fromWorld) {
        String normalName = configManager.getWorldNameNormal();
        String netherName = configManager.getWorldNameNether();
        String endName = configManager.getWorldNameEnd();

        Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());

        World targetWorld = null;
        Location targetLoc;

        if (fromWorld.getName().equals(normalName) || fromWorld.getName().equals(netherName)) {
            targetWorld = worldManager.getOrCreateSkyblockEnd();
        } else if (fromWorld.getName().equals(endName)) {
            targetWorld = worldManager.getOrCreateSkyblockWorld();
        }

        if (targetWorld != null) {
            if (optionalIsland.isPresent()) {
                targetLoc = getIslandLocation(optionalIsland.get(), targetWorld);
            } else {
                if (fromWorld.getName().equals(endName)) {
                    targetLoc = targetWorld.getSpawnLocation();
                } else {
                    targetLoc = new Location(targetWorld, 100, 50, 0);
                }
            }
            event.setTo(targetLoc);
        }
    }

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

    private double[] getTeleportOffsetsByWorldType(World world, Island island) {
        String worldName = world.getName();
        String netherName = worldManager.getSkyblockNether().getName();
        String endName = worldManager.getSkyblockEnd().getName();

        Island.WorldType worldType;
        if (worldName.equals(netherName)) {
            worldType = Island.WorldType.NETHER;
        } else if (worldName.equals(endName)) {
            worldType = Island.WorldType.END;
        } else {
            worldType = Island.WorldType.NORMAL;
        }

        String schematicId = (island != null) ? island.getSchematicId() : null;
        return configManager.getTeleportOffsetsBySchematicAndWorldType(schematicId, worldType);
    }
}
