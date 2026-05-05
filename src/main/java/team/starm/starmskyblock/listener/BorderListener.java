package team.starm.starmskyblock.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class BorderListener implements Listener {

    private final StarMSkyblock plugin;
    private final IslandManager islandManager;
    private final Map<UUID, Boolean> playerBorderSettings = new HashMap<>();

    public BorderListener(StarMSkyblock plugin) {
        this.plugin = plugin;
        this.islandManager = plugin.getIslandManager();
    }

    public boolean isPlayerShowBorder(UUID playerUuid) {
        return playerBorderSettings.getOrDefault(playerUuid, true);
    }

    public void setPlayerShowBorder(UUID playerUuid, boolean show) {
        playerBorderSettings.put(playerUuid, show);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayerBorder(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }
        updatePlayerBorder(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        updatePlayerBorder(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        int fromChunkX = from.getChunk().getX();
        int fromChunkZ = from.getChunk().getZ();
        int toChunkX = to.getChunk().getX();
        int toChunkZ = to.getChunk().getZ();

        if (fromChunkX == toChunkX && fromChunkZ == toChunkZ) {
            return;
        }

        updatePlayerBorder(event.getPlayer());
    }

    public void updatePlayerBorder(Player player) {
        updatePlayerBorder(player, player.getLocation());
    }

    public void updatePlayerBorder(Player player, Location location) {
        World playerWorld = location.getWorld();
        if (playerWorld == null) {
            player.setWorldBorder(null);
            return;
        }

        List<World> skyblockWorlds = Arrays.asList(
                plugin.getWorldManager().getSkyblockWorld(),
                plugin.getWorldManager().getSkyblockNether(),
                plugin.getWorldManager().getSkyblockEnd());

        boolean isInSkyblockWorld = skyblockWorlds.stream().anyMatch(w -> w != null && w.equals(playerWorld));
        if (!isInSkyblockWorld) {
            player.setWorldBorder(null);
            return;
        }

        if (!isPlayerShowBorder(player.getUniqueId())) {
            player.setWorldBorder(null);
            return;
        }

        Optional<Island> currentIsland = IslandPermissionManager.getPlayerCurrentIslandMaxRange(islandManager, location);
        currentIsland.ifPresentOrElse(
                island -> player.setWorldBorder(createIslandBorder(island)),
                () -> player.setWorldBorder(null));
    }

    public static WorldBorder createIslandBorder(Island island) {
        int radiusChunks = island.getEffectiveMaxRadius();
        double sideLength = (radiusChunks * 2 + 1) * 16.0;

        int centerChunkX = island.getCenterChunkX();
        int centerChunkZ = island.getCenterChunkZ();
        double centerX = centerChunkX * 16.0 + 8.0;
        double centerZ = centerChunkZ * 16.0 + 8.0;

        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(sideLength);
        border.setWarningDistance(0);

        return border;
    }
}
