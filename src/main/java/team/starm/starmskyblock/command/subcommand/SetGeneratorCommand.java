package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.config.GeneratorConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /isadmin setgenerator <岛屿ID> <等级>} 子命令 -- 设置指定岛屿的方块生成器等级。
 * <p>
 * 仅在生成器功能启用时生效，等级须在 1 至配置上限之间。
 */
public class SetGeneratorCommand extends AdminSubCommand {

    public SetGeneratorCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 解析岛屿 ID 与生成器等级，校验启用状态与等级范围后写入。
     */
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
