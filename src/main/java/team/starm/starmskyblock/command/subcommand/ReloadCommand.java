package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

public class ReloadCommand extends AdminSubCommand {

    public ReloadCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        long start = System.currentTimeMillis();

        plugin.getConfigManager().reload();
        plugin.getGeneratorConfigManager().reload();
        plugin.getUpgradeConfigManager().reload();
        plugin.getSignConfigManager().reloadSignConfig();
        plugin.getSettingsConfigManager().reloadSettingsConfig();
        plugin.getPermissionConfigManager().reloadPermissionsConfig();
        plugin.getLanguageManager().reload();

        long elapsed = System.currentTimeMillis() - start;
        MessageUtil.sendMessage(sender, "&a所有配置文件已重载！(耗时 " + elapsed + "ms)");
        return true;
    }
}
