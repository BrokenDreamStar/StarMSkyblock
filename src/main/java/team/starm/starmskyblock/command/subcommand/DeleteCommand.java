package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandDeleteTask;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;
import java.util.Optional;

public class DeleteCommand extends SubCommand {

    public DeleteCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (!island.getOwnerId().equals(player.getUniqueId())) {
            MessageUtil.send(player, "island.delete.owner-only");
            return true;
        }

        if (!assertMaxArgs(player, args, 2, "/is delete [confirm]")) return true;

        if (args.length == 1 || !args[1].equalsIgnoreCase("confirm")) {
            MessageUtil.send(player, "island.delete.confirm-warning");
            return true;
        }

        var islandManager = plugin.getIslandManager();
        int deleteCount = islandManager.getDeleteCount(player.getUniqueId());
        int maxDeleteTimes = plugin.getConfigManager().getMaxDeleteTimes();
        if (deleteCount >= maxDeleteTimes) {
            MessageUtil.send(player, "island.delete.max-reached", Map.of("max", maxDeleteTimes));
            return true;
        }

        World mainWorld = Bukkit.getWorlds().getFirst();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.getWorldManager().isSkyblockWorld(p.getWorld())) continue;
            if (islandManager.isPlayerOnIsland(p, island)) {
                p.teleport(mainWorld.getSpawnLocation());
                MessageUtil.send(p, "island.delete.ejected");
            }
        }

        islandManager.removeIslandFromMemory(island);
        MessageUtil.send(player, "island.delete.started");
        MessageUtil.send(player, "island.delete.success");

        IslandDeleteTask deleteTask = new IslandDeleteTask(plugin, islandManager, island,
                player.getUniqueId(), deleteCount, maxDeleteTimes);
        deleteTask.runTaskAsynchronously(plugin);
        return true;
    }
}
