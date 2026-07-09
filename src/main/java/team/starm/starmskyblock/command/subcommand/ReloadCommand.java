package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Map;

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
        plugin.getSettingsConfigManager().reloadSettingsConfig();
        plugin.getPermissionConfigManager().reloadPermissionsConfig();
        plugin.getPublicAreaConfigManager().reload();
        plugin.getLockedAreaConfigManager().reload();
        plugin.getLanguageManager().reload();

        long elapsed = System.currentTimeMillis() - start;
        MessageUtil.send(sender, "command.admin.reload-success", Map.of("elapsed", elapsed));
        return true;
    }
}
