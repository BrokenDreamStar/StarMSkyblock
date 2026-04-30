package team.starm.starmskyblock.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;

import java.util.Optional;

public class PlayerNetherListener implements Listener {

    private final StarMSkyblock plugin;

    public PlayerNetherListener(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        World fromWorld = from.getWorld();

        if (fromWorld == null)
            return;

        String normalName = plugin.getConfigManager().getWorldNameNormal();
        String netherName = plugin.getConfigManager().getWorldNameNether();

        // 只处理下界传送门事件
        if (event.getCause() != TeleportCause.NETHER_PORTAL) {
            return;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> optionalIsland = islandManager.getIsland(player.getUniqueId());

        if (optionalIsland.isEmpty()) {
            return; // 玩家没有岛屿，使用默认逻辑
        }

        Island island = optionalIsland.get();

        // 从主世界到下界
        if (fromWorld.getName().equals(normalName)) {
            handleToNether(event, player, island);
        }
        // 从下界到主世界
        else if (fromWorld.getName().equals(netherName)) {
            handleFromNether(event, player, island);
        }
    }

    /**
     * 处理从主世界到下界的传送
     */
    private void handleToNether(PlayerPortalEvent event, Player player, Island island) {
        World targetWorld = plugin.getWorldManager().getOrCreateSkyblockNether();
        if (targetWorld == null)
            return;

        // 使用原版传送门逻辑
        Location targetLoc = calculateNetherPortalLocation(event.getFrom(), targetWorld);

        // 检查传送门创建位置是否在岛屿范围内
        if (!isLocationWithinIsland(targetLoc, island)) {
            player.sendMessage("§c无法创建传送门：目标位置超出你的岛屿范围！");
            event.setCancelled(true);
            return;
        }

        event.setTo(targetLoc);
    }

    /**
     * 处理从下界到主世界的传送
     */
    private void handleFromNether(PlayerPortalEvent event, Player player, Island island) {
        World targetWorld = plugin.getWorldManager().getOrCreateSkyblockWorld();
        if (targetWorld == null)
            return;

        // 使用原版传送门逻辑
        Location targetLoc = calculateOverworldPortalLocation(event.getFrom(), targetWorld);

        // 检查传送门创建位置是否在岛屿范围内
        if (!isLocationWithinIsland(targetLoc, island)) {
            player.sendMessage("§c无法创建传送门：目标位置超出你的岛屿范围！");
            event.setCancelled(true);
            return;
        }

        event.setTo(targetLoc);
    }

    /**
     * 计算下界传送门位置（原版逻辑）
     */
    private Location calculateNetherPortalLocation(Location from, World targetWorld) {
        // 原版坐标缩放逻辑：主世界坐标 / 8
        return new Location(targetWorld, from.getX() / 8.0, from.getY(), from.getZ() / 8.0,
                from.getYaw(), from.getPitch());
    }

    /**
     * 计算主世界传送门位置（原版逻辑）
     */
    private Location calculateOverworldPortalLocation(Location from, World targetWorld) {
        // 原版坐标缩放逻辑：下界坐标 * 8
        return new Location(targetWorld, from.getX() * 8.0, from.getY(), from.getZ() * 8.0,
                from.getYaw(), from.getPitch());
    }

    /**
     * 检查位置是否在岛屿范围内
     */
    private boolean isLocationWithinIsland(Location location, Island island) {
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();
        return island.isChunkWithinIsland(chunkX, chunkZ);
    }
}