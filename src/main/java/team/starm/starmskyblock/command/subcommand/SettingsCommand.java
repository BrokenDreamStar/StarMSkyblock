package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.setting.IslandSetting;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Optional;

public class SettingsCommand extends SubCommand {

    public SettingsCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.sendMessage(player, "&c你还没有岛屿！");
            return true;
        }

        Island island = optionalIsland.get();

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.EDIT_SETTINGS)) {
            MessageUtil.sendMessage(player, "&c你没有权限修改岛屿设置！");
            return true;
        }

        if (args.length == 1) {
            return displaySettings(player, island);
        }

        String settingKey = args[1].toLowerCase();

        IslandSetting setting = IslandSetting.fromKey(settingKey);
        if (setting == null) {
            MessageUtil.sendMessage(player, "&c未知的设置项: &e" + settingKey);
            MessageUtil.sendMessage(player, "&c可用设置项: &e/is settings &7查看所有设置");
            return true;
        }

        boolean currentVal = island.getSetting(setting);

        if (args.length < 3) {
            String status = currentVal ? "&a已启用" : "&c已禁用";
            MessageUtil.sendMessage(player, "&e" + setting.getDisplayName() + " &f| " + status);
            MessageUtil.sendMessage(player, "&7使用 &e/is settings " + settingKey
                    + " toggle &7切换，或 &e/is settings " + settingKey + " <true|false> &7直接设置。");
            return true;
        }

        boolean value;
        if (args[2].equalsIgnoreCase("toggle")) {
            value = !currentVal;
        } else if (args[2].equalsIgnoreCase("true")) {
            value = true;
        } else if (args[2].equalsIgnoreCase("false")) {
            value = false;
        } else {
            MessageUtil.sendMessage(player, "&c值必须为 true 或 false！");
            return true;
        }

        island.setSetting(setting, value);

        if (plugin.getIslandManager().updateIslandSettings(island.getId(), island)) {
            MessageUtil.sendMessage(player, "&a设置项 &e" + setting.getDisplayName()
                    + " &a已" + (value ? "&a启用" : "&c禁用"));
        } else {
            MessageUtil.sendMessage(player, "&c设置保存失败，请稍后重试。");
        }
        return true;
    }

    private boolean displaySettings(Player player, Island island) {
        MessageUtil.sendMessage(player, "&a=== 岛屿设置 ===");

        for (IslandSetting setting : IslandSetting.values()) {
            boolean val = island.getSetting(setting);
            String status = val ? "&a✔ 启用" : "&c✘ 禁用";
            MessageUtil.sendMessage(player, "&e" + setting.getDisplayName() + " &7("
                    + setting.getConfigKey() + ")" + " &f| " + status);
        }
        MessageUtil.sendMessage(player, "&7使用 &e/is settings <设置项> <true|false> &7来修改设置。");
        return true;
    }
}
