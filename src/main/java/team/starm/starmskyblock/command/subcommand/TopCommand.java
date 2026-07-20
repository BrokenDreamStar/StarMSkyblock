package team.starm.starmskyblock.command.subcommand;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Map;

/**
 * {@code /is top} 子命令 —— 岛屿等级排行榜。
 * <p>
 * 按岛屿等级降序显示排行榜，每页 {@value #PER_PAGE} 个岛屿。
 * 翻页方式与 {@code /is list} 一致：{@code next/prev/home}。
 * <p>
 * 分页状态通过 {@code SkyblockExpansion} 委托至 {@code IslandListHandler}，
 * 与 {@code island_list} 的页码独立维护。
 */
public class TopCommand extends SubCommand {

    private static final int PER_PAGE = 10;

    public TopCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        var expansion = plugin.getSkyblockExpansion();
        if (expansion == null) {
            MessageUtil.send(player, "island.top.papi-not-loaded");
            return true;
        }

        var handler = expansion.getIslandListHandler();
        List<Island> sorted = handler.getSortedIslandsPublic();

        if (sorted.isEmpty()) {
            MessageUtil.send(player, "island.top.empty");
            return true;
        }

        int totalPages = Math.max(1,
                (int) Math.ceil((double) sorted.size() / PER_PAGE));

        // 无操作参数时直接显示当前页
        if (args.length < 2) {
            int page = handler.getTopPlayerPage(player);
            showPage(player, sorted, page, totalPages);
            return true;
        }

        if (!assertMaxArgs(player, args, 2, "/is top <next|prev|home>")) return true;

        String action = args[1].toLowerCase();
        int currentPage = handler.getTopPlayerPage(player);

        switch (action) {
            case "next" -> {
                if (currentPage >= totalPages) {
                    MessageUtil.send(player, "island.top.last-page");
                    return true;
                }
                handler.setTopPlayerPage(player, currentPage + 1);
                showPage(player, sorted, currentPage + 1, totalPages);
            }
            case "prev" -> {
                if (currentPage <= 1) {
                    MessageUtil.send(player, "island.top.first-page");
                    return true;
                }
                handler.setTopPlayerPage(player, currentPage - 1);
                showPage(player, sorted, currentPage - 1, totalPages);
            }
            case "home" -> {
                handler.resetTopPlayerPage(player);
                showPage(player, sorted, 1, totalPages);
            }
            default -> MessageUtil.send(player, "island.top.usage");
        }

        return true;
    }

    private void showPage(Player player, List<Island> sorted, int page, int totalPages) {
        MessageUtil.send(player, "island.top.header",
                Map.of("page", page, "total", totalPages));

        int start = (page - 1) * PER_PAGE;
        int end = Math.min(start + PER_PAGE, sorted.size());

        for (int i = start; i < end; i++) {
            Island island = sorted.get(i);
            String ownerName = plugin.getPlayerRepo()
                    .getPlayerName(island.getOwnerId())
                    .orElse("未知");
            String islandName = island.getName();
            if (islandName == null || islandName.isBlank()) {
                islandName = "岛屿 #" + island.getId();
            }

            MessageUtil.send(player, "island.top.entry",
                    Map.of("rank", i + 1,
                            "name", islandName,
                            "level", island.getLevel(),
                            "owner", ownerName));
        }

        MessageUtil.send(player, "island.top.page",
                Map.of("current", page, "total", totalPages));
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
