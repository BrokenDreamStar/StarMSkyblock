package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.message.MessageUtil;

public class BorderCommand extends SubCommand {

    public BorderCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        var borderListener = plugin.getBorderListener();

        if (args.length < 2) {
            boolean current = borderListener.isPlayerShowBorder(player.getUniqueId());
            MessageUtil.sendMessage(player, "&a岛屿边界显示: " + (current ? "&a已开启" : "&c已关闭"));
            MessageUtil.sendMessage(player, "&7使用 &e/is border toggle &7切换，或 &e/is border <true|false> &7直接设置。");
            return true;
        }

        boolean show;
        if (args[1].equalsIgnoreCase("toggle")) {
            show = !borderListener.isPlayerShowBorder(player.getUniqueId());
        } else if (args[1].equalsIgnoreCase("true")) {
            show = true;
        } else if (args[1].equalsIgnoreCase("false")) {
            show = false;
        } else {
            MessageUtil.sendMessage(player, "&c用法: /is border [true|false|toggle]");
            return true;
        }

        borderListener.setPlayerShowBorder(player.getUniqueId(), show);

        if (show) {
            MessageUtil.sendMessage(player, "&a岛屿边界显示已开启！");
        } else {
            MessageUtil.sendMessage(player, "&c岛屿边界显示已关闭！");
        }

        borderListener.updatePlayerBorder(player);
        return true;
    }
}
