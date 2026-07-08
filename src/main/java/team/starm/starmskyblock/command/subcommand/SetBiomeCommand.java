package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.util.SkyblockBiome;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class SetBiomeCommand extends SubCommand {

    public SetBiomeCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        var optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        World playerWorld = player.getLocation().getWorld();
        var worldManager = plugin.getWorldManager();

        if (!worldManager.isSkyblockWorld(playerWorld)) {
            MessageUtil.send(player, "biome.skyblock-only");
            return true;
        }

        if (worldManager.isEndWorld(playerWorld.getName())) {
            MessageUtil.send(player, "biome.end-not-supported");
            return true;
        }

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_BIOME)) {
            MessageUtil.send(player, "biome.no-permission");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "biome.set.usage");
            return true;
        }

        if (!assertMaxArgs(player, args, 3, "/is setbiome <生物群系> [confirm]")) return true;

        SkyblockBiome target = SkyblockBiome.fromInput(args[1]);
        if (target == null) {
            MessageUtil.send(player, "biome.unknown", Map.of("input", args[1]));
            return true;
        }

        boolean isNether = worldManager.isNetherWorld(playerWorld.getName());
        SkyblockBiome.Dimension currentDim = isNether ? SkyblockBiome.Dimension.NETHER : SkyblockBiome.Dimension.OVERWORLD;
        if (target.getDimension() != currentDim) {
            MessageUtil.send(player, "biome.dimension-not-available");
            return true;
        }

        Biome bukkitBiome = target.toBukkitBiome();
        if (bukkitBiome == null) {
            MessageUtil.send(player, "biome.load-failed");
            return true;
        }

        int effectiveMax = island.getEffectiveMaxRadius();
        int totalChunks = (effectiveMax * 2 + 1) * (effectiveMax * 2 + 1);

        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            MessageUtil.send(player, "biome.set.confirm-warning", Map.of(
                    "count", totalChunks,
                    "biome", target.getDisplayName()));
            MessageUtil.send(player, "biome.set.confirm-hint", Map.of("biome", args[1]));
            return true;
        }

        int centerX = island.getCenterChunkX();
        int centerZ = island.getCenterChunkZ();
        int minY = playerWorld.getMinHeight();
        int maxY = playerWorld.getMaxHeight();

        int modifiedChunks = 0;
        for (int cx = centerX - effectiveMax; cx <= centerX + effectiveMax; cx++) {
            for (int cz = centerZ - effectiveMax; cz <= centerZ + effectiveMax; cz++) {
                int bx = cx << 4;
                int bz = cz << 4;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y += 4) {
                            playerWorld.setBiome(bx + x, y, bz + z, bukkitBiome);
                        }
                    }
                }

                playerWorld.refreshChunk(cx, cz);
                modifiedChunks++;
            }
        }

        MessageUtil.send(player, "biome.set.success", Map.of("biome", target.getColoredDisplayName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            World world = player.getWorld();
            var worldManager = plugin.getWorldManager();
            SkyblockBiome.Dimension dim = worldManager.isNetherWorld(world.getName())
                    ? SkyblockBiome.Dimension.NETHER
                    : SkyblockBiome.Dimension.OVERWORLD;
            String prefix = args[1];
            return SkyblockBiome.displayNamesFor(dim).stream()
                    .filter(b -> b.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
