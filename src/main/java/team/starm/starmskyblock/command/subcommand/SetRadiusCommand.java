package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

public class SetRadiusCommand extends AdminSubCommand {

    public SetRadiusCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 3) {
            MessageUtil.sendMessage(sender, "&c用法: /isadmin setradius <岛屿ID> <新半径>");
            return true;
        }

        int islandId;
        try {
            islandId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c岛屿ID必须是整数！");
            return true;
        }

        int newRadius;
        try {
            newRadius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c新半径必须是整数！");
            return true;
        }

        int maxRadius = plugin.getConfigManager().getIslandMaxRadius();
        if (newRadius > maxRadius) {
            MessageUtil.sendMessage(sender, "&c新的半径不能超过配置文件中的最大半径限制: " + maxRadius);
            return true;
        }

        if (newRadius <= 0) {
            MessageUtil.sendMessage(sender, "&c半径必须大于0！");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> optionalIsland = islandManager.getIsland(islandId);

        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(sender, "&c找不到ID为 " + islandId + " 的岛屿！");
            return true;
        }

        Island island = optionalIsland.get();
        islandManager.updateIslandRadius(island.getId(), newRadius);
        MessageUtil.sendMessage(sender, "&a成功将岛屿 &e#" + islandId + " &a的半径修改为 &e" + newRadius + " &a区块。");
        return true;
    }
}
