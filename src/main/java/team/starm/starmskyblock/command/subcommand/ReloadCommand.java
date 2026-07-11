package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;

/**
 * {@code /isadmin reload} 子命令 -- 重载全部配置与语言文件。
 * <p>
 * 按固定顺序依次重载各配置管理器，最后重载语言文件，完成后报告耗时(毫秒)。
 * 新增配置管理器时须在此补充 reload 调用。
 */
public class ReloadCommand extends AdminSubCommand {

    public ReloadCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 依次重载所有配置管理器与语言文件，完成后发送耗时。
     */
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        long start = System.currentTimeMillis();

        plugin.getConfigManager().reload();
        plugin.getExperienceConfig().reload();
        plugin.getAuraskillsContributionConfig().reload();
        plugin.getGeneratorConfigManager().reload();
        plugin.getUpgradeConfigManager().reload();
        plugin.getSettingsConfigManager().reloadSettingsConfig();
        plugin.getPermissionConfigManager().reloadPermissionsConfig();
        plugin.getPublicAreaConfigManager().reload();
        plugin.getLockedAreaConfigManager().reload();
        plugin.getTaskConfigManager().scan();
        plugin.getLanguageManager().reload();

        long elapsed = System.currentTimeMillis() - start;
        MessageUtil.send(sender, "command.admin.reload-success", Map.of("elapsed", elapsed));
        return true;
    }
}
