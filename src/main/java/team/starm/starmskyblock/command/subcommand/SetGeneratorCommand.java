package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;
import java.util.Optional;

public class SetGeneratorCommand extends AdminSubCommand {

    public SetGeneratorCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 3) {
            MessageUtil.send(sender, "generator.set.usage");
            return true;
        }

        int islandId;
        try {
            islandId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "generator.set.island-id-not-int");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "generator.set.level-not-int");
            return true;
        }

        GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
        if (!genConfig.isEnabled()) {
            MessageUtil.send(sender, "generator.set.not-enabled");
            return true;
        }

        int maxLevel = genConfig.getMaxLevel();
        if (level < 1 || level > maxLevel) {
            MessageUtil.send(sender, "generator.set.level-out-of-range", Map.of("max", maxLevel));
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> optionalIsland = islandManager.getIsland(islandId);

        if (optionalIsland.isEmpty()) {
            MessageUtil.send(sender, "generator.set.island-id-not-found", Map.of("id", islandId));
            return true;
        }

        islandManager.updateIslandGeneratorLevel(islandId, level);
        MessageUtil.send(sender, "generator.set.success", Map.of("id", islandId, "level", level));
        return true;
    }
}
