package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.command.IslandCommand;

/**
 * {@code /is help} 子命令 -- 显示帮助信息。
 * <p>
 * 委托给 {@link IslandCommand#sendHelpMessage(Player)} 输出子命令列表。
 */
public class HelpCommand extends SubCommand {

    private final IslandCommand islandCommand;

    public HelpCommand(StarMSkyblock plugin, IslandCommand islandCommand) {
        super(plugin);
        this.islandCommand = islandCommand;
    }

    @Override
    public boolean execute(Player player, String[] args) {
        islandCommand.sendHelpMessage(player);
        return true;
    }
}
