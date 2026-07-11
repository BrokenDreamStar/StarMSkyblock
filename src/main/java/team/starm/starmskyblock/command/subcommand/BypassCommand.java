package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.BasePermissionManager;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /isadmin bypass [on|off|toggle|status]
 * <p>
 * 控制全局"无视岛屿权限"开关。默认关闭：所有玩家(含OP)均需遵守岛屿权限；
 * 开启后恢复原 bypass 行为(OP 与 skyblock.bypass 权限节点持有者绕过)。
 * 开关仅存内存，服务器重启后恢复为关闭。
 */
public class BypassCommand extends AdminSubCommand {

    public BypassCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 执行 /isadmin bypass 命令：按子参数切换全局无视权限开关或查询当前状态。
     */
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // args[0] = "bypass"；args[1] = on|off|toggle|status（缺省 -> 查询状态）
        if (args.length < 2) {
            sendStatus(sender);
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "on" -> {
                BasePermissionManager.setBypassEnabled(true);
                MessageUtil.send(sender, "command.admin.bypass-enabled");
            }
            case "off" -> {
                BasePermissionManager.setBypassEnabled(false);
                MessageUtil.send(sender, "command.admin.bypass-disabled");
            }
            case "toggle" -> {
                boolean now = !BasePermissionManager.isBypassEnabled();
                BasePermissionManager.setBypassEnabled(now);
                MessageUtil.send(sender, now ? "command.admin.bypass-enabled" : "command.admin.bypass-disabled");
            }
            case "status" -> sendStatus(sender);
            default -> MessageUtil.send(sender, "command.admin.usage-bypass");
        }
        return true;
    }

    /** 向发送方报告当前 bypass 开关的启用/关闭状态。 */
    private void sendStatus(CommandSender sender) {
        MessageUtil.send(sender, BasePermissionManager.isBypassEnabled()
                ? "command.admin.bypass-status-on" : "command.admin.bypass-status-off");
    }

    /** Tab 补全：第二参数补全 on/off/toggle/status。 */
    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("on", "off", "toggle", "status").stream()
                    .filter(option -> option.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
