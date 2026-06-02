package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.message.MessageUtil;

public class ListCommand extends SubCommand {

    public ListCommand(team.starm.starmskyblock.StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        var expansion = plugin.getSkyblockExpansion();
        if (expansion == null) {
            MessageUtil.sendMessage(player, "&cPlaceholderAPI 未加载，无法使用此功能。");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&c用法: /is list <next|prev|home>");
            return true;
        }

        if (!assertMaxArgs(player, args, 2, "/is list <next|prev|home>")) return true;

        String action = args[1].toLowerCase();
        int currentPage = expansion.getPlayerPage(player);
        int totalIslands = plugin.getIslandManager().getAllIslands().size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalIslands / 28));

        switch (action) {
            case "next" -> {
                if (currentPage >= totalPages) {
                    MessageUtil.sendMessage(player, "&c已经是最后一页了。");
                    return true;
                }
                expansion.setPlayerPage(player, currentPage + 1);
                MessageUtil.sendMessage(player, "&a已切换到第 &e" + (currentPage + 1) + " &a页，共 &e" + totalPages + " &a页");
            }
            case "prev" -> {
                if (currentPage <= 1) {
                    MessageUtil.sendMessage(player, "&c已经在第一页了。");
                    return true;
                }
                expansion.setPlayerPage(player, currentPage - 1);
                MessageUtil.sendMessage(player, "&a已切换到第 &e" + (currentPage - 1) + " &a页，共 &e" + totalPages + " &a页");
            }
            case "spawn", "first" -> {
                expansion.resetPlayerPage(player);
                MessageUtil.sendMessage(player, "&a已返回第 1 页，共 &e" + totalPages + " &a页");
            }
            default -> MessageUtil.sendMessage(player, "&c用法: /is list <next|prev|home>");
        }

        return true;
    }
}
