package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.Island.WorldType;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;
import java.util.Optional;

public class SpawnCommand extends SubCommand {

    public SpawnCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 2, "/is spawn [confirm]")) return true;

        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "spawn.no-island-hint");
            return true;
        }

        Island island = optionalIsland.get();
        Location spawnLocation;
        World targetWorld;

        if (island.hasCustomHome()) {
            WorldType worldType = island.getCustomHomeWorldType();
            targetWorld = switch (worldType) {
                case NETHER -> plugin.getWorldManager().getSkyblockNether();
                case END -> plugin.getWorldManager().getSkyblockEnd();
                default -> plugin.getWorldManager().getSkyblockWorld();
            };
            spawnLocation = new Location(targetWorld,
                    island.getCustomHomeX(), island.getCustomHomeY(), island.getCustomHomeZ(),
                    island.getCustomHomeYaw(), island.getCustomHomePitch());

            if (!isLocationSafe(spawnLocation) && (args.length == 1 || !args[1].equalsIgnoreCase("confirm"))) {
                MessageUtil.send(player, "spawn.unsafe-warning");
                MessageUtil.send(player, "spawn.force-hint");
                return true;
            }

            String worldName = worldType == WorldType.NORMAL ? "主世界"
                    : worldType == WorldType.NETHER ? "下界" : "末地";
            MessageUtil.send(player, "spawn.teleported-custom", Map.of("world", worldName));
        } else {
            targetWorld = plugin.getWorldManager().getSkyblockWorld();
            ConfigManager config = plugin.getConfigManager();
            double[] offsets = config.getTeleportOffsetsBySchematicAndWorldType(
                    island.getSchematicId(), WorldType.NORMAL);

            double teleportX = (island.getCenterChunkX() * 16) + 8 + offsets[0];
            double teleportY = config.getIslandHeight() + offsets[1];
            double teleportZ = (island.getCenterChunkZ() * 16) + 8 + offsets[2];

            spawnLocation = new Location(targetWorld, teleportX, teleportY, teleportZ,
                    (float) offsets[3], (float) offsets[4]);

            if (!isLocationSafe(spawnLocation) && (args.length == 1 || !args[1].equalsIgnoreCase("confirm"))) {
                MessageUtil.send(player, "spawn.unsafe-warning");
                MessageUtil.send(player, "spawn.force-hint");
                return true;
            }

            MessageUtil.send(player, "spawn.welcome-back");
        }

        player.teleport(spawnLocation);
        return true;
    }
}
