package team.starm.starmskyblock.command.subcommand;

import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

public class SetSpawnCommand extends SubCommand {

    public SetSpawnCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 1, "/is setspawn")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        var playerLocation = player.getLocation();
        World playerWorld = playerLocation.getWorld();
        var worldManager = plugin.getWorldManager();

        if (!worldManager.isSkyblockWorld(playerWorld)) {
            MessageUtil.send(player, "spawn.set.skyblock-dimension-only");
            return true;
        }

        if (!plugin.getIslandManager().isPlayerOnIsland(player, island)) {
            MessageUtil.send(player, "spawn.set.within-island-only");
            return true;
        }

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_SPAWN)) {
            MessageUtil.send(player, "spawn.set.no-permission");
            return true;
        }

        org.bukkit.Location blockBelow = playerLocation.clone().subtract(0, 1, 0);
        if (blockBelow.getBlock().getType().isAir()) {
            MessageUtil.send(player, "spawn.set.no-block-below");
            return true;
        }

        Island.WorldType worldType = playerWorld.equals(worldManager.getSkyblockNether())
                ? Island.WorldType.NETHER
                : playerWorld.equals(worldManager.getSkyblockEnd())
                  ? Island.WorldType.END
                  : Island.WorldType.NORMAL;

        var configManager = plugin.getConfigManager();
        if (worldType == Island.WorldType.NETHER && !configManager.isAllowSetspawnInNether()) {
            MessageUtil.send(player, "spawn.set.nether-not-supported");
            return true;
        }
        if (worldType == Island.WorldType.END && !configManager.isAllowSetspawnInEnd()) {
            MessageUtil.send(player, "spawn.set.end-not-supported");
            return true;
        }

        if (plugin.getIslandManager().updateIslandCustomHome(island.getId(), worldType,
                playerLocation.getX(), playerLocation.getY(), playerLocation.getZ(),
                playerLocation.getYaw(), playerLocation.getPitch())) {
            MessageUtil.send(player, "spawn.set.success");
        } else {
            MessageUtil.send(player, "general.operation-failed");
        }
        return true;
    }
}
