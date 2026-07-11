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
import team.starm.starmskyblock.StarMSkyblock;

/**
 * {@code /is spawn} 子命令 -- 传送回岛屿出生点。
 * <p>
 * 优先使用自定义出生点(由 {@code /is setspawn} 设置)；无自定义出生点时
 * 按蓝图偏移回到岛屿默认生成位置。位置不安全时需 {@code confirm} 确认强制传送。
 */
public class SpawnCommand extends SubCommand {

    public SpawnCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 计算出生点位置并传送。位置不安全时提示需 confirm 确认。
     */
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
