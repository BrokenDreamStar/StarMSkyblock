package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.level.LevelManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

/**
 * {@code /is level} 子命令 —— 触发岛屿等级计算。
 * <p>
 * 调用 {@link LevelManager#calculateIsland(Island, Player)}
 * 开始异步扫描岛屿全境方块，完成后显示结果。
 */
public class LevelCommand extends SubCommand {

    public LevelCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 1, "/is level")) return true;

        Optional<Island> islandOpt = getIsland(player);
        if (islandOpt.isEmpty()) {
            MessageUtil.send(player, "island.level.no-island");
            return true;
        }

        Island island = islandOpt.get();
        LevelManager levelManager = plugin.getLevelManager();

        levelManager.calculateIsland(island, player);
        return true;
    }
}