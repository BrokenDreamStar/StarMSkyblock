package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.manager.ManagementPermissionManager;
import team.starm.starmskyblock.setting.IslandSetting;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import team.starm.starmskyblock.StarMSkyblock;

/**
 * 岛屿设置命令（/is settings [设置项] [true|false|toggle]）
 * <p>
 * 查看或切换岛屿设置（如爆炸、火焰蔓延、PvP、幻翼生成等开关）。
 * 仅持有 EDIT_SETTINGS 权限的成员可修改，无参数时列出全部设置项及其当前状态。
 */
public class SettingsCommand extends SubCommand {

    public SettingsCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 执行 /is settings 命令：无参数则列出全部设置；带设置项与开关值则切换指定设置。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();

        if (ManagementPermissionManager.lacksPermission(island, player.getUniqueId(), IslandPermission.EDIT_SETTINGS)) {
            MessageUtil.send(player, "settings.no-permission");
            return true;
        }

        if (args.length == 1) {
            return displaySettings(player, island);
        }

        if (!assertMaxArgs(player, args, 3, "/is settings <设置项> <true|false>")) return true;

        String settingKey = args[1].toLowerCase();

        IslandSetting setting = IslandSetting.fromKey(settingKey);
        if (setting == null) {
            MessageUtil.send(player, "settings.unknown-key", Map.of("key", settingKey));
            MessageUtil.send(player, "settings.unknown-key-hint");
            return true;
        }

        boolean currentVal = island.getSetting(setting);

        if (args.length < 3) {
            String currentValueKey = currentVal ? "settings.current-value-enabled" : "settings.current-value-disabled";
            MessageUtil.send(player, currentValueKey, Map.of("name", setting.getDisplayName()));
            MessageUtil.send(player, "settings.toggle.hint", Map.of("key", settingKey));
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
            MessageUtil.send(player, "settings.invalid-value");
            return true;
        }

        island.setSetting(setting, value);

        if (plugin.getIslandManager().updateIslandSettings(island.getId())) {
            String successKey = value ? "settings.toggle.success-enabled" : "settings.toggle.success-disabled";
            MessageUtil.send(player, successKey, Map.of("name", setting.getDisplayName()));
        } else {
            MessageUtil.send(player, "settings.save-failed");
        }
        return true;
    }

    /**
     * 列出全部岛屿设置项及其当前启用/禁用状态。
     */
    private boolean displaySettings(Player player, Island island) {
        MessageUtil.send(player, "settings.list.header");

        for (IslandSetting setting : IslandSetting.values()) {
            boolean val = island.getSetting(setting);
            String entryKey = val ? "settings.list.entry-enabled" : "settings.list.entry-disabled";
            MessageUtil.send(player, entryKey, Map.of(
                    "name", setting.getDisplayName(),
                    "key", setting.getConfigKey()));
        }
        MessageUtil.send(player, "settings.list.footer");
        return true;
    }

    /**
     * Tab 补全：第二参数补设置项 key，第三参数补 true/false。
     */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return Arrays.stream(IslandSetting.values())
                    .map(IslandSetting::getConfigKey)
                    .filter(key -> key.startsWith(prefix))
                    .toList();
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase();
            return List.of("true", "false").stream()
                    .filter(v -> v.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
