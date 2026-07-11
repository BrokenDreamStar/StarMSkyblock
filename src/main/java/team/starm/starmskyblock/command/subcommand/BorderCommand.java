package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.StarMSkyblock;

import java.util.List;

/**
 * {@code /is border} 子命令 -- 查看或切换岛屿边界显示。
 * <p>
 * 支持 {@code true}/{@code false}/{@code toggle} 参数，无参数时显示当前状态。
 */
public class BorderCommand extends SubCommand {

    public BorderCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 无参数显示当前边界状态；有参数则设置并立即刷新该玩家的边界。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        var borderListener = plugin.getBorderListener();

        if (args.length < 2) {
            boolean current = borderListener.isPlayerShowBorder(player.getUniqueId());
            if (current) {
                MessageUtil.send(player, "border.show.status-enabled");
            } else {
                MessageUtil.send(player, "border.show.status-disabled");
            }
            MessageUtil.send(player, "border.show.hint");
            return true;
        }

        if (!assertMaxArgs(player, args, 2, "/is border [true|false|toggle]")) return true;

        boolean show;
        if (args[1].equalsIgnoreCase("toggle")) {
            show = !borderListener.isPlayerShowBorder(player.getUniqueId());
        } else if (args[1].equalsIgnoreCase("true")) {
            show = true;
        } else if (args[1].equalsIgnoreCase("false")) {
            show = false;
        } else {
            MessageUtil.send(player, "border.usage");
            return true;
        }

        borderListener.setPlayerShowBorder(player.getUniqueId(), show);

        if (show) {
            MessageUtil.send(player, "border.enabled");
        } else {
            MessageUtil.send(player, "border.disabled");
        }

        borderListener.updatePlayerBorder(player);
        return true;
    }

    /** Tab 补全 true/false/toggle。 */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("true", "false", "toggle").stream()
                    .filter(v -> v.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
