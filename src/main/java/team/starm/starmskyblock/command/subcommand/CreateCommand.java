package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.IslandCreateTask;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.StarMSkyblock;

import java.util.Map;

/**
 * {@code /is create} 子命令 -- 创建岛屿。
 * <p>
 * 解析岛屿类型(蓝图 id)与名称后，委托 {@link IslandCreateTask} 异步执行
 * 网格定位、DB 写入、贴蓝图等操作。
 */
public class CreateCommand extends SubCommand {

    public CreateCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 解析参数并启动岛屿创建任务。
     * <p>
     * 已有岛屿则拒绝；第一个参数若匹配蓝图 id 则作为类型，否则整体作为岛屿名；
     * 未指定名称时默认使用"玩家名 + 的岛屿"。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 3, "/is create [类型] [名称]")) return true;

        var islandManager = plugin.getIslandManager();
        if (islandManager.getIsland(player.getUniqueId()).isPresent()) {
            MessageUtil.send(player, "island.create.already-have");
            return true;
        }

        String schematicId = plugin.getConfigManager().getDefaultNormalSchematicId();
        String islandName = null;

        if (args.length > 1) {
            String firstArg = args[1].toLowerCase();
            if (plugin.getConfigManager().getNormalSchematics().containsKey(firstArg)) {
                schematicId = firstArg;
                if (args.length > 2) {
                    islandName = args[2];
                }
            } else {
                islandName = args[1];
            }
        }

        if (islandName == null || islandName.isEmpty()) {
            islandName = player.getName() + "的岛屿";
        }

        MessageUtil.send(player, "island.create.divider");
        MessageUtil.send(player, "island.create.starting");
        MessageUtil.send(player, "island.create.type", Map.of("type", schematicId));
        MessageUtil.send(player, "island.create.name", Map.of("name", islandName));
        MessageUtil.send(player, "island.create.divider");

        IslandCreateTask createTask = new IslandCreateTask(plugin, islandManager,
                player.getUniqueId(), schematicId, islandName);
        createTask.runTaskAsynchronously(plugin);
        return true;
    }
}
