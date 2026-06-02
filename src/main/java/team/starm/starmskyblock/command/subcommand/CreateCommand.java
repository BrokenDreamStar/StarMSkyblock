package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.IslandCreateTask;
import team.starm.starmskyblock.message.MessageUtil;

public class CreateCommand extends SubCommand {

    public CreateCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 3, "/is create [类型] [名称]")) return true;

        var islandManager = plugin.getIslandManager();
        if (islandManager.getIsland(player.getUniqueId()).isPresent()) {
            MessageUtil.sendMessage(player, "&c你已经拥有一个岛屿了！使用 /is spawn 返回。");
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

        MessageUtil.sendMessage(player, "&a================================");
        MessageUtil.sendMessage(player, "&a开始异步创建岛屿...");
        MessageUtil.sendMessage(player, "&a岛屿类型: &e" + schematicId);
        MessageUtil.sendMessage(player, "&a岛屿名称: &e" + islandName);
        MessageUtil.sendMessage(player, "&a================================");

        IslandCreateTask createTask = new IslandCreateTask(plugin, islandManager,
                player.getUniqueId(), schematicId, islandName);
        createTask.runTaskAsynchronously(plugin);
        return true;
    }
}
