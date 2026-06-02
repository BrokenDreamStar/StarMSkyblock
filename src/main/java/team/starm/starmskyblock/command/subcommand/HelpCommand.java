package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.command.IslandCommand;

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
