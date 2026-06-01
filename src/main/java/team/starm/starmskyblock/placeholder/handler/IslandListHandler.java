package team.starm.starmskyblock.placeholder.handler;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.permission.IslandPermissionLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class IslandListHandler {

    public static final String PREFIX = "island_list_";

    private static final String SIZE = "island_list_size";
    private static final String MAX_PAGE = "island_list_max_page";
    private static final String IS_LAST_PAGE = "island_list_is_last_page";

    private static final Pattern ITEM_PATTERN =
            Pattern.compile("island_list_(\\d+)(?:_(\\w+))?", Pattern.CASE_INSENSITIVE);

    private static final int ISLANDS_PER_PAGE = 28;

    private final StarMSkyblock plugin;
    private volatile List<Island> sortedIslandsCache;
    private volatile long sortedIslandsCacheTime;
    private final Map<UUID, Integer> playerListPages = new ConcurrentHashMap<>();

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

    public void warmUpSortedCache() {
        getSortedIslands();
    }

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
            throwable.printStackTrace();
        }

        return null;
    }

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

                default -> null;
            };

        } catch (Throwable throwable) {
            throwable.printStackTrace();
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
                result.append("&f- ").append(color).append(name).append("(").append(color).append(roleName).append(")");
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
