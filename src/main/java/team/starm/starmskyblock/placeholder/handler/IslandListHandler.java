package team.starm.starmskyblock.placeholder.handler;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermissionLevel;

import team.starm.starmskyblock.message.MessageUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 岛屿排行榜占位符处理器（前缀 {@code island_list_}）。
 * <p>
 * 提供按等级降序排列的岛屿列表渲染：分页（每页 {@value #ISLANDS_PER_PAGE} 项）、
 * 槽位字段（名称/岛主/等级/成员/创建时间）等。
 * <p>
 * 排序列表带 1 秒 TTL 缓存（{@link #sortedIslandsCache}），避免每次 PAPI 刷新都重新排序全量岛屿。
 */
public class IslandListHandler {

    /** 占位符前缀（岛屿列表，分页槽位） */
    public static final String PREFIX = "island_list_";
    /** 占位符前缀（岛屿排行榜，绝对排名） */
    public static final String TOP_PREFIX = "island_top_";

    private static final String SIZE = "island_list_size";
    private static final String MAX_PAGE = "island_list_max_page";
    private static final String IS_LAST_PAGE = "island_list_is_last_page";

    private static final Pattern ITEM_PATTERN =
            Pattern.compile("island_list_(\\d+)(?:_(\\w+))?", Pattern.CASE_INSENSITIVE);
    /** 绝对排名占位符：island_top_<rank>_<field> 或 island_top_<field>（无排名 = 自己岛屿） */
    private static final Pattern TOP_ITEM_PATTERN =
            Pattern.compile("island_top_(\\d+)_(\\w+)", Pattern.CASE_INSENSITIVE);

    private static final int ISLANDS_PER_PAGE = 28;
    /** 排行榜每页显示数 */
    private static final int TOP_PER_PAGE = 10;

    private final StarMSkyblock plugin;
    /** 按等级降序排列的岛屿缓存（volatile：写仅 reload 线程，读多线程）。 */
    private volatile List<Island> sortedIslandsCache;
    /** 缓存写入时间戳，TTL 1 秒 */
    private volatile long sortedIslandsCacheTime;
    /** 每个玩家当前查看的排行榜页码（用于多行占位符渲染） */
    private final Map<UUID, Integer> playerListPages = new ConcurrentHashMap<>();
    /** 每个玩家当前查看的 top 排行榜页码（与 island_list 页码独立） */
    private final Map<UUID, Integer> playerTopPages = new ConcurrentHashMap<>();

    public IslandListHandler(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void setPlayerPage(Player player, int page) {
        if (player == null) return;
        if (page < 1) page = 1;
        playerListPages.put(player.getUniqueId(), page);
    }

    public int getPlayerPage(Player player) {
        if (player == null) return 1;
        return playerListPages.getOrDefault(player.getUniqueId(), 1);
    }

    public void resetPlayerPage(Player player) {
        if (player == null) return;
        playerListPages.remove(player.getUniqueId());
    }

    /** 设置玩家的 top 排行榜页码（1-based）。 */
    public void setTopPlayerPage(Player player, int page) {
        if (player == null) return;
        if (page < 1) page = 1;
        playerTopPages.put(player.getUniqueId(), page);
    }

    /** 获取玩家的 top 排行榜页码（默认第 1 页）。 */
    public int getTopPlayerPage(Player player) {
        if (player == null) return 1;
        return playerTopPages.getOrDefault(player.getUniqueId(), 1);
    }

    /** 重置玩家的 top 排行榜页码（回到第 1 页）。 */
    public void resetTopPlayerPage(Player player) {
        if (player == null) return;
        playerTopPages.remove(player.getUniqueId());
    }

    /** 获取总页数（每页 {@value #TOP_PER_PAGE} 项）。 */
    public int getTopTotalPages() {
        return Math.max(1,
                (int) Math.ceil((double) getSortedIslands().size() / TOP_PER_PAGE));
    }

    /** 公开排序缓存供 {@code TopCommand} 使用。 */
    public List<Island> getSortedIslandsPublic() {
        return getSortedIslands();
    }

    public void warmUpSortedCache() {
        getSortedIslands();
    }

    /**
     * 处理岛屿排行榜占位符请求。
     *
     * @param player 请求占位符的玩家
     * @param params 占位符参数（含前缀）
     * @return 渲染结果，无法识别时返回 null
     */
    public String handle(Player player, String params) {

        try {

            if (params.equalsIgnoreCase(SIZE)) {
                return String.valueOf(getSortedIslands().size());
            }

            if (params.equalsIgnoreCase("island_list_page")) {
                return String.valueOf(getPlayerPage(player));
            }

            if (params.equalsIgnoreCase(MAX_PAGE)) {

                int maxPage = Math.max(
                        1,
                        (int) Math.ceil((double) getSortedIslands().size() / ISLANDS_PER_PAGE)
                );

                return String.valueOf(maxPage);
            }

            if (params.equalsIgnoreCase(IS_LAST_PAGE)) {

                int currentPage = getPlayerPage(player);

                int maxPage = Math.max(
                        1,
                        (int) Math.ceil((double) getSortedIslands().size() / ISLANDS_PER_PAGE)
                );

                return currentPage >= maxPage ? "true" : "false";
            }

            return handleListItem(player, params);

        } catch (Throwable throwable) {
            MessageUtil.consoleError("处理岛屿列表占位符时发生错误", throwable);
        }

        return null;
    }

    /**
     * 处理 {@code island_top_} 前缀的排行榜占位符。
     * <p>
     * 支持两种形式：
     * <ul>
     *   <li>{@code island_top_<rank>_<field>} —— 绝对排名（1-based），取第 rank 名的岛屿字段</li>
     *   <li>{@code island_top_<field>} —— 无排名号，返回玩家自己岛屿的字段</li>
     * </ul>
     * 字段：name/id/owner/level/members/memberlist/creationtime（与 island_list 一致）。
     *
     * @param player 请求占位符的玩家
     * @param params 完整占位符参数（含前缀）
     * @return 渲染结果，无法识别时返回 null
     */
    public String handleTop(Player player, String params) {
        try {
            // 先尝试匹配 island_top_<rank>_<field> 格式
            var matcher = TOP_ITEM_PATTERN.matcher(params);
            if (matcher.matches()) {
                int rank;
                try {
                    rank = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return null;
                }
                String field = matcher.group(2);
                List<Island> sorted = getSortedIslands();
                if (rank < 1 || rank > sorted.size()) {
                    return "NONE";
                }
                Island island = sorted.get(rank - 1);
                return getFieldValue(island, field);
            }

            // 再尝试 island_top_<field>（无排名号，返回自己岛屿）
            if (params.startsWith("island_top_")) {
                String field = params.substring("island_top_".length());
                if (field.isEmpty()) return null;

                var islandOpt = plugin.getIslandManager()
                        .getIslandByPlayer(player.getUniqueId());
                if (islandOpt.isEmpty()) return "NONE";

                // 特例：rank 字段返回岛屿在全局排行榜中的排名
                if (field.equalsIgnoreCase("rank")) {
                    Island own = islandOpt.get();
                    List<Island> sorted = getSortedIslands();
                    for (int i = 0; i < sorted.size(); i++) {
                        if (sorted.get(i).getId() == own.getId()) {
                            return String.valueOf(i + 1);
                        }
                    }
                    return "N/A";
                }

                return getFieldValue(islandOpt.get(), field);
            }

        } catch (Throwable throwable) {
            MessageUtil.consoleError("处理岛屿排行榜占位符时发生错误", throwable);
        }

        return null;
    }

    /** 从岛屿提取指定字段的值（复用 island_list 的字段逻辑）。 */
    private String getFieldValue(Island island, String field) {
        if (field == null || field.equalsIgnoreCase("name")) {
            String islandName = island.getName();
            if (islandName == null || islandName.isBlank()) {
                return "岛屿 #" + island.getId();
            }
            return islandName;
        }

        return switch (field.toLowerCase()) {
            case "id" -> String.valueOf(island.getId());
            case "owner" -> getOwnerName(island);
            case "level" -> String.valueOf(island.getLevel());
            case "members" -> String.valueOf(island.getMembers().size() + 1);
            case "memberlist" -> getMemberList(island);
            case "creationtime" -> island.getCreatedAt();
            default -> null;
        };
    }

    /** 获取按等级降序排列的岛屿列表（带 1 秒 TTL 缓存）。 */
    private List<Island> getSortedIslands() {

        long now = System.currentTimeMillis();

        if (sortedIslandsCache != null
                && now - sortedIslandsCacheTime < 1000) {

            return sortedIslandsCache;
        }

        List<Island> list =
                new ArrayList<>(plugin.getIslandManager().getAllIslands());

        list.sort((a, b) -> {

            int levelCompare =
                    Integer.compare(b.getLevel(), a.getLevel());

            if (levelCompare != 0) {
                return levelCompare;
            }

            return Integer.compare(a.getId(), b.getId());
        });

        sortedIslandsCache = list;
        sortedIslandsCacheTime = now;

        return list;
    }

    /** 解析 {@code island_list_<slot>_<field>} 格式的列表项占位符，按玩家当前页码定位槽位。 */
    private String handleListItem(Player player, String params) {

        try {

            var matcher = ITEM_PATTERN.matcher(params);

            if (!matcher.matches()) {
                return null;
            }

            int slotNumber;

            try {
                slotNumber = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }

            int page = getPlayerPage(player);

            int actualIndex =
                    (page - 1) * ISLANDS_PER_PAGE + slotNumber - 1;

            List<Island> sorted = getSortedIslands();

            if (actualIndex < 0 || actualIndex >= sorted.size()) {
                return "NONE";
            }

            Island island = sorted.get(actualIndex);

            String field = matcher.group(2);

            if (field == null || field.equalsIgnoreCase("name")) {

                String islandName = island.getName();

                if (islandName == null || islandName.isBlank()) {
                    return "岛屿 #" + island.getId();
                }

                return islandName;
            }

            return switch (field.toLowerCase()) {

                case "id" -> String.valueOf(island.getId());

                case "owner" -> getOwnerName(island);

                case "level" -> String.valueOf(island.getLevel());

                case "members" -> String.valueOf(island.getMembers().size() + 1);

                case "memberlist" -> getMemberList(island);

                case "creationtime" -> island.getCreatedAt();

                default -> null;
            };

        } catch (Throwable throwable) {
            MessageUtil.consoleError("处理岛屿列表项占位符时发生错误", throwable);
        }

        return null;
    }

    private String getMemberList(Island island) {

        Map<IslandPermissionLevel, List<String>> groups = new LinkedHashMap<>();

        groups.put(IslandPermissionLevel.OWNER, new ArrayList<>());
        groups.put(IslandPermissionLevel.ADMIN, new ArrayList<>());
        groups.put(IslandPermissionLevel.MOD, new ArrayList<>());
        groups.put(IslandPermissionLevel.MEMBER, new ArrayList<>());

        for (var entry : island.getMembers().entrySet()) {

            List<String> list = groups.get(entry.getValue());

            if (list != null) {

                String name = plugin.getPlayerRepo()
                        .getPlayerName(entry.getKey())
                        .orElseGet(() -> {
                            OfflinePlayer op =
                                    Bukkit.getOfflinePlayer(entry.getKey());

                            return op.getName() != null
                                    ? op.getName()
                                    : "未知";
                        });

                list.add(name);
            }
        }

        String ownerName = getOwnerName(island);
        groups.get(IslandPermissionLevel.OWNER).add(ownerName);

        StringBuilder result = new StringBuilder();

        for (var entry : groups.entrySet()) {

            if (entry.getValue().isEmpty()) {
                continue;
            }

            String color = entry.getKey().getColor();
            String roleName = entry.getKey().getDisplayName();

            for (String name : entry.getValue()) {
                if (!result.isEmpty()) {
                    result.append("\n");
                }
                result.append("&f - ").append(color).append(name).append("(").append(color).append(roleName).append(")");
            }
        }

        return !result.isEmpty()
                ? result.toString().stripTrailing()
                : null;
    }

    private String getOwnerName(Island island) {

        try {

            Optional<String> name =
                    plugin.getPlayerRepo()
                            .getPlayerName(island.getOwnerId());

            if (name.isPresent()) {
                return name.get();
            }

            OfflinePlayer player =
                    Bukkit.getOfflinePlayer(island.getOwnerId());

            String playerName = player.getName();

            return playerName != null
                    ? playerName
                    : "未知";

        } catch (Throwable ignored) {
        }

        return "未知";
    }
}
