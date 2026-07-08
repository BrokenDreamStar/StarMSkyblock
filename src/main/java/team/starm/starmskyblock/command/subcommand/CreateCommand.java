package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.IslandCreateTask;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;

public class CreateCommand extends SubCommand {

    public CreateCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

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
