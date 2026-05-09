package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Optional;

public class PortalListener implements Listener {

    private final ConfigManager configManager;
    private final SkyblockWorldManager worldManager;
    private final IslandManager islandManager;

    public PortalListener(ConfigManager configManager, SkyblockWorldManager worldManager, IslandManager islandManager) {
        this.configManager = configManager;
        this.worldManager = worldManager;
        this.islandManager = islandManager;
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

        Location targetLoc = calculateNetherPortalLocation(event.getFrom(), targetWorld);
        if (!isLocationWithinIsland(targetLoc, island)) {
            player.sendMessage("§c无法创建传送门：目标位置超出你的岛屿范围！");
            event.setCancelled(true);
            return;
        }
        event.setTo(targetLoc);
    }

    private void handleFromNether(PlayerPortalEvent event, Player player, Island island) {
        World targetWorld = worldManager.getOrCreateSkyblockWorld();
        if (targetWorld == null) return;

        Location targetLoc = calculateOverworldPortalLocation(event.getFrom(), targetWorld);
        if (!isLocationWithinIsland(targetLoc, island)) {
            player.sendMessage("§c无法创建传送门：目标位置超出你的岛屿范围！");
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

    private boolean isLocationWithinIsland(Location location, Island island) {
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();
        return island.isChunkWithinIsland(chunkX, chunkZ);
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
