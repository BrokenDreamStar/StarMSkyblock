package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

public class SetGeneratorCommand extends AdminSubCommand {

    public SetGeneratorCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 3) {
            MessageUtil.sendMessage(sender, "&c用法: /isadmin setgenerator <岛屿ID> <等级>");
            return true;
        }

        int islandId;
        try {
            islandId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c岛屿ID必须是整数！");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(sender, "&c刷石机等级必须是整数！");
            return true;
        }

        GeneratorConfigManager genConfig = plugin.getGeneratorConfigManager();
        if (!genConfig.isEnabled()) {
            MessageUtil.sendMessage(sender, "&c刷石机功能未启用！");
            return true;
        }

        int maxLevel = genConfig.getMaxLevel();
        if (level < 1 || level > maxLevel) {
            MessageUtil.sendMessage(sender, "&c等级必须在 1 ~ " + maxLevel + " 之间！");
            return true;
        }

        IslandManager islandManager = plugin.getIslandManager();
        Optional<Island> optionalIsland = islandManager.getIsland(islandId);

        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(sender, "&c找不到ID为 " + islandId + " 的岛屿！");
            return true;
        }

        islandManager.updateIslandGeneratorLevel(islandId, level);
        MessageUtil.sendMessage(sender, "&a成功将岛屿 &e#" + islandId + " &a的刷石机等级设置为 &e" + level);
        return true;
    }
}
