package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandDeleteTask;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

public class DeleteCommand extends SubCommand {

    public DeleteCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        if (!island.getOwnerId().equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c只有岛主才能删除岛屿！");
            return true;
        }

        if (!assertMaxArgs(player, args, 2, "/is delete [confirm]")) return true;

        if (args.length == 1 || !args[1].equalsIgnoreCase("confirm")) {
            MessageUtil.sendMessage(player, "&c警告：这将永久删除你的岛屿！使用 &e/is delete confirm &c确认。");
            return true;
        }

        var islandManager = plugin.getIslandManager();
        int deleteCount = islandManager.getDeleteCount(player.getUniqueId());
        int maxDeleteTimes = plugin.getConfigManager().getMaxDeleteTimes();
        if (deleteCount >= maxDeleteTimes) {
            MessageUtil.sendMessage(player, "&c你已达到删除上限 (" + maxDeleteTimes + ")！");
            return true;
        }

        World mainWorld = Bukkit.getWorlds().getFirst();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (islandManager.isPlayerOnIsland(p, island)) {
                p.teleport(mainWorld.getSpawnLocation());
                MessageUtil.sendMessage(p, "&c由于当前所在岛屿被删除，你已被传送到出生点。");
            }
        }

        islandManager.removeIslandFromMemory(island);
        MessageUtil.sendMessage(player, "&a岛屿删除操作已开始执行，请稍候...");
        MessageUtil.sendMessage(player, "&e岛屿已删除");

        IslandDeleteTask deleteTask = new IslandDeleteTask(plugin, islandManager, island,
                player.getUniqueId(), deleteCount, maxDeleteTimes);
        deleteTask.runTaskAsynchronously(plugin);
        return true;
    }
}
