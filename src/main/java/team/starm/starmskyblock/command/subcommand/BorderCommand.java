package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;

public class BorderCommand extends SubCommand {

    public BorderCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

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
