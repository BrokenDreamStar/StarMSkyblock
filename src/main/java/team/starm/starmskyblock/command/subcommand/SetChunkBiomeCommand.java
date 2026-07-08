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
public class SetChunkBiomeCommand extends SubCommand {

    public SetChunkBiomeCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
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

        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();

        if (!island.isChunkWithinIsland(chunkX, chunkZ)) {
            if (island.isChunkWithinMaxRange(chunkX, chunkZ)) {
                MessageUtil.send(player, "biome.chunk.area-locked");
            } else {
                MessageUtil.send(player, "biome.chunk.out-of-island");
            }
            return true;
        }

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_BIOME)) {
            MessageUtil.send(player, "biome.no-permission");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "biome.chunk.usage");
            return true;
        }

        if (!assertMaxArgs(player, args, 2, "/is setchunkbiome <生物群系>")) return true;

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

        Chunk chunk = player.getLocation().getChunk();
        int bx = chunk.getX() << 4;
        int bz = chunk.getZ() << 4;
        int minY = playerWorld.getMinHeight();
        int maxY = playerWorld.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y += 4) {
                    playerWorld.setBiome(bx + x, y, bz + z, bukkitBiome);
                }
            }
        }

        playerWorld.refreshChunk(chunk.getX(), chunk.getZ());

        MessageUtil.send(player, "biome.chunk.success", Map.of("biome", target.getColoredDisplayName()));
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
