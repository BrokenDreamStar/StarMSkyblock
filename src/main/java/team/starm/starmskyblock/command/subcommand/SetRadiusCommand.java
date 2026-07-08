package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;
import java.util.Optional;

public class SetRadiusCommand extends AdminSubCommand {

    public SetRadiusCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 3) {
            MessageUtil.send(sender, "radius.set.usage");
            return true;
        }

        int islandId;
        try {
            islandId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "radius.set.island-id-not-int");
            return true;
        }

        int newRadius;
        try {
            newRadius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "radius.set.not-int");
            return true;
        }

        int maxRadius = plugin.getConfigManager().getIslandMaxRadius();
        if (newRadius > maxRadius) {
            MessageUtil.send(sender, "radius.set.exceeds-max", Map.of("max", maxRadius));
            return true;
        }

        if (newRadius <= 0) {
            MessageUtil.send(sender, "radius.set.must-be-positive");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> optionalIsland = islandManager.getIsland(islandId);

        if (optionalIsland.isEmpty()) {
            MessageUtil.send(sender, "radius.set.island-id-not-found", Map.of("id", islandId));
            return true;
        }

        Island island = optionalIsland.get();
        islandManager.updateIslandRadius(island.getId(), newRadius);
        MessageUtil.send(sender, "radius.set.success", Map.of("id", islandId, "radius", newRadius));
        return true;
    }
}
