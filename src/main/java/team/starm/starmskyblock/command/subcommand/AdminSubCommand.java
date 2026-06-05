package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;

import java.util.Collections;
import java.util.List;

public abstract class AdminSubCommand {

    protected final StarMSkyblock plugin;

    public AdminSubCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public abstract boolean execute(CommandSender sender, String[] args);

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
