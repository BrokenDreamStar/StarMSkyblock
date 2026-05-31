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
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        World playerWorld = player.getLocation().getWorld();
        var worldManager = plugin.getWorldManager();

        if (!worldManager.isSkyblockWorld(playerWorld)) {
            MessageUtil.sendMessage(player, "&c你只能在空岛世界修改生物群系！");
            return true;
        }

        if (worldManager.isEndWorld(playerWorld.getName())) {
            MessageUtil.sendMessage(player, "&c末地维度不支持修改生物群系！");
            return true;
        }

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.SET_BIOME)) {
            MessageUtil.sendMessage(player, "&c你没有权限修改岛屿生物群系！");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法: /is setbiome <生物群系>");
            return true;
        }

        SkyblockBiome target = SkyblockBiome.fromInput(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(player, "&c未知生物群系: " + args[1]);
            return true;
        }

        boolean isNether = worldManager.isNetherWorld(playerWorld.getName());
        SkyblockBiome.Dimension currentDim = isNether ? SkyblockBiome.Dimension.NETHER : SkyblockBiome.Dimension.OVERWORLD;
        if (target.getDimension() != currentDim) {
            MessageUtil.sendMessage(player, "&c该生物群系在当前维度不可用！");
            return true;
        }

        Biome bukkitBiome = target.toBukkitBiome();
        if (bukkitBiome == null) {
            MessageUtil.sendMessage(player, "&c无法加载生物群系数据，请稍后重试。");
            return true;
        }

        int effectiveMax = island.getEffectiveMaxRadius();
        int totalChunks = (effectiveMax * 2 + 1) * (effectiveMax * 2 + 1);

        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            MessageUtil.sendMessage(player, "&c警告：此操作将修改整个岛屿（共 " + totalChunks + " 个区块）的生物群系为 " + target.getDisplayName() + "！");
            MessageUtil.sendMessage(player, "&e请使用 &b/is setbiome " + args[1] + " confirm &e确认操作。");
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

        MessageUtil.sendMessage(player, "&a已成功将岛屿的生物群系设置为 " + target.getDisplayName() + " &a（共修改 " + modifiedChunks + " 个区块）！");
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
