package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Map;
import team.starm.starmskyblock.StarMSkyblock;

/**
 * {@code /is list} 子命令 -- 浏览岛屿列表翻页。
 * <p>
 * 依赖 PlaceholderAPI 扩展的页面状态，支持 next/prev/first 翻页操作。
 */
public class ListCommand extends SubCommand {

    public ListCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    /**
     * 执行翻页操作。PAPI 未加载或无参数时提示用法。
     */
    @Override
    public boolean execute(Player player, String[] args) {
        var expansion = plugin.getSkyblockExpansion();
        if (expansion == null) {
            MessageUtil.send(player, "island.list.papi-not-loaded");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "island.list.usage");
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
                    MessageUtil.send(player, "island.list.last-page");
                    return true;
                }
                expansion.setPlayerPage(player, currentPage + 1);
                MessageUtil.send(player, "island.list.page-switched",
                        Map.of("current", currentPage + 1, "total", totalPages));
            }
            case "prev" -> {
                if (currentPage <= 1) {
                    MessageUtil.send(player, "island.list.first-page");
                    return true;
                }
                expansion.setPlayerPage(player, currentPage - 1);
                MessageUtil.send(player, "island.list.page-switched",
                        Map.of("current", currentPage - 1, "total", totalPages));
            }
            case "spawn", "first" -> {
                expansion.resetPlayerPage(player);
                MessageUtil.send(player, "island.list.page-reset", Map.of("total", totalPages));
            }
            default -> MessageUtil.send(player, "island.list.usage");
        }

        return true;
    }

    /** Tab 补全 next/prev/home。 */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("next", "prev", "home").stream()
                    .filter(v -> v.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
