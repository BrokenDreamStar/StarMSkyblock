package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.world.SkyblockWorldManager;

import java.util.Map;
import java.util.Optional;
import java.util.List;

public class PortalInfoCommand extends SubCommand {

    public PortalInfoCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return true;

        SkyblockWorldManager worldManager = plugin.getWorldManager();
        String worldName = world.getName();
        boolean fromNormal = worldManager.isNormalWorld(worldName);
        boolean fromNether = worldManager.isNetherWorld(worldName);
        boolean fromEnd = worldManager.isEndWorld(worldName);

        if (!fromNormal && !fromNether && !fromEnd) {
            MessageUtil.send(player, "island.portal.world-not-supported");
            return true;
        }

        if (fromEnd) {
            MessageUtil.send(player, "island.portal.end-dimension");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> islandOpt = islandManager.getIslandByPlayer(player.getUniqueId());
        if (islandOpt.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }
        Island island = islandOpt.get();

        // 复用 PortalListener 的中心偏移公式
        double centerX = island.getCenterChunkX() * 16.0 + 8.0;
        double centerZ = island.getCenterChunkZ() * 16.0 + 8.0;
        double offsetX = loc.getX() - centerX;
        double offsetZ = loc.getZ() - centerZ;

        double targetX, targetZ;
        String currentName, targetName;

        if (fromNormal) {
            targetX = centerX + offsetX / 8.0;
            targetZ = centerZ + offsetZ / 8.0;
            currentName = "主世界";
            targetName = "下界";
        } else {
            targetX = centerX + offsetX * 8.0;
            targetZ = centerZ + offsetZ * 8.0;
            currentName = "下界";
            targetName = "主世界";
        }

        int targetChunkX = (int) Math.floor(targetX) >> 4;
        int targetChunkZ = (int) Math.floor(targetZ) >> 4;
        boolean withinIsland = island.isChunkWithinIsland(targetChunkX, targetChunkZ);
        boolean withinMax = island.isChunkWithinMaxRange(targetChunkX, targetChunkZ);

        MessageUtil.send(player, "island.portal.current-position", Map.of(
                "world", currentName,
                "x", String.format("%.0f", loc.getX()),
                "y", String.format("%.0f", loc.getY()),
                "z", String.format("%.0f", loc.getZ())
        ));
        MessageUtil.send(player, "island.portal.target-position", Map.of(
                "world", targetName,
                "x", String.format("%.0f", targetX),
                "y", String.format("%.0f", loc.getY()),
                "z", String.format("%.0f", targetZ)
        ));

        if (withinIsland) {
            MessageUtil.send(player, "island.portal.within-island-yes");
        } else if (withinMax) {
            MessageUtil.send(player, "island.portal.within-island-locked");
        } else {
            MessageUtil.send(player, "island.portal.within-island-out");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        return List.of();
    }
}