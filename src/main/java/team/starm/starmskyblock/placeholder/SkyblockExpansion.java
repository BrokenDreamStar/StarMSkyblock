package team.starm.starmskyblock.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.setting.IslandSetting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class SkyblockExpansion extends PlaceholderExpansion {

    private final StarMSkyblock plugin;

    public SkyblockExpansion(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "starmskyblock";
    }

    @Override
    public String getAuthor() {
        return "StarM Team";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private static final String EMPTY = "";

    private static final String PERMISSION_PREFIX = "permission_";
    private static final String PERMISSION_SUFFIX = "_level_weight";
    private static final String LEVEL_SEPARATOR = "_level_";
    private static final String SETTINGS_PREFIX = "islandsettings_";
    private static final String HAS_PERMISSION_PREFIX = "haspermission_";
    private static final String ISLAND_LIST_PREFIX = "island_list_";
    private static final String ISLAND_LIST_SIZE = "island_list_size";
    private static final String ISLAND_LIST_MAX_PAGE = "island_list_max_page";
    private static final String ISLAND_LIST_IS_LAST_PAGE = "island_list_is_last_page";

    private static final Pattern ISLAND_LIST_ITEM_PATTERN =
            Pattern.compile("island_list_(\\d+)(?:_(\\w+))?", Pattern.CASE_INSENSITIVE);

    private final AtomicInteger papiCallCount = new AtomicInteger(0);

    private volatile List<Island> sortedIslandsCache;
    private volatile long sortedIslandsCacheTime;

    private final Map<UUID, Integer> playerListPages = new ConcurrentHashMap<>();

    private static final int ISLANDS_PER_PAGE = 28;

    public int getPapiCallCount() {
        return papiCallCount.get();
    }

    public void resetPapiCallCount() {
        papiCallCount.set(0);
    }

    public void setPlayerPage(Player player, int page) {

        if (player == null) {
            return;
        }

        if (page < 1) {
            page = 1;
        }

        playerListPages.put(player.getUniqueId(), page);
    }

    public int getPlayerPage(Player player) {

        if (player == null) {
            return 1;
        }

        return playerListPages.getOrDefault(player.getUniqueId(), 1);
    }

    public void resetPlayerPage(Player player) {

        if (player == null) {
            return;
        }

        playerListPages.remove(player.getUniqueId());
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {

        papiCallCount.incrementAndGet();

        try {

            if (params == null || params.isBlank()) {
                return EMPTY;
            }

            if (params.equalsIgnoreCase("call_count")) {
                return String.valueOf(papiCallCount.get());
            }

            if (params.equalsIgnoreCase("call_count_reset")) {
                return String.valueOf(papiCallCount.getAndSet(0));
            }

            if (player == null) {
                return EMPTY;
            }

            IslandManager islandManager = plugin.getIslandManager();

            int chunkX = player.getLocation().getChunk().getX();
            int chunkZ = player.getLocation().getChunk().getZ();

            if (params.equalsIgnoreCase("island")) {
                return getIslandName(islandManager, chunkX, chunkZ);
            }

            if (params.equalsIgnoreCase("role")) {
                return getPlayerRole(islandManager, chunkX, chunkZ, player.getUniqueId());
            }

            if (params.equalsIgnoreCase(ISLAND_LIST_SIZE)) {
                return String.valueOf(getSortedIslands().size());
            }

            if (params.equalsIgnoreCase("island_list_page")) {
                return String.valueOf(getPlayerPage(player));
            }

            if (params.equalsIgnoreCase(ISLAND_LIST_MAX_PAGE)) {

                int maxPage = Math.max(
                        1,
                        (int) Math.ceil((double) getSortedIslands().size() / ISLANDS_PER_PAGE)
                );

                return String.valueOf(maxPage);
            }

            if (params.equalsIgnoreCase(ISLAND_LIST_IS_LAST_PAGE)) {

                int currentPage = getPlayerPage(player);

                int maxPage = Math.max(
                        1,
                        (int) Math.ceil((double) getSortedIslands().size() / ISLANDS_PER_PAGE)
                );

                return currentPage >= maxPage ? "true" : "false";
            }

            if (params.equalsIgnoreCase("creationtime")) {

                Optional<Island> islandOpt =
                        islandManager.getIslandByPlayer(player.getUniqueId());

                if (islandOpt.isEmpty()) {
                    return EMPTY;
                }

                String time = islandOpt.get().getCreatedAt();

                return time != null ? time : EMPTY;
            }

            if (params.regionMatches(
                    true,
                    0,
                    ISLAND_LIST_PREFIX,
                    0,
                    ISLAND_LIST_PREFIX.length()
            )) {

                return handleIslandListItem(player, params);
            }

            if (params.regionMatches(
                    true,
                    0,
                    SETTINGS_PREFIX,
                    0,
                    SETTINGS_PREFIX.length()
            )) {

                String settingName =
                        params.substring(SETTINGS_PREFIX.length());

                return getIslandSettingStatus(
                        islandManager,
                        player.getUniqueId(),
                        settingName
                );
            }

            if (params.regionMatches(
                    true,
                    0,
                    PERMISSION_PREFIX,
                    0,
                    PERMISSION_PREFIX.length()
            )) {

                if (params.regionMatches(
                        true,
                        params.length() - PERMISSION_SUFFIX.length(),
                        PERMISSION_SUFFIX,
                        0,
                        PERMISSION_SUFFIX.length()
                )) {

                    String permName =
                            params.substring(
                                    PERMISSION_PREFIX.length(),
                                    params.length() - PERMISSION_SUFFIX.length()
                            );

                    return getPermissionLevelWeight(
                            islandManager,
                            player.getUniqueId(),
                            permName
                    );
                }

                String rest = params.substring(PERMISSION_PREFIX.length());

                int separatorIdx = rest.lastIndexOf(LEVEL_SEPARATOR);

                if (separatorIdx != -1) {

                    String permName = rest.substring(0, separatorIdx);

                    String roleName =
                            rest.substring(separatorIdx + LEVEL_SEPARATOR.length());

                    return getPermissionRoleStatus(
                            islandManager,
                            player.getUniqueId(),
                            permName,
                            roleName
                    );
                }
            }

            if (params.regionMatches(
                    true,
                    0,
                    HAS_PERMISSION_PREFIX,
                    0,
                    HAS_PERMISSION_PREFIX.length()
            )) {

                String permName =
                        params.substring(HAS_PERMISSION_PREFIX.length());

                if (permName.isBlank()) {
                    return "false";
                }

                try {

                    IslandPermission permission =
                            IslandPermission.valueOf(permName.toUpperCase());

                    Optional<Island> islandOpt =
                            islandManager.getIslandByPlayer(player.getUniqueId());

                    if (islandOpt.isPresent()) {

                        return islandOpt.get()
                                .hasPermission(player.getUniqueId(), permission)
                                ? "true"
                                : "false";
                    }

                } catch (IllegalArgumentException ignored) {
                }

                return "false";
            }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return EMPTY;
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

    private String handleIslandListItem(Player player, String params) {

        try {

            var matcher = ISLAND_LIST_ITEM_PATTERN.matcher(params);

            if (!matcher.matches()) {
                return EMPTY;
            }

            int slotNumber;

            try {
                slotNumber = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return EMPTY;
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

                default -> EMPTY;
            };

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return EMPTY;
    }

    private String getOwnerName(Island island) {

        try {

            Optional<String> name =
                    plugin.getSqliteManager()
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

    private String getIslandName(
            IslandManager islandManager,
            int chunkX,
            int chunkZ
    ) {

        try {

            Optional<Island> islandOpt =
                    islandManager.getIslandAt(chunkX, chunkZ);

            if (islandOpt.isEmpty()) {
                islandOpt =
                        islandManager.getIslandAtMaxRange(chunkX, chunkZ);
            }

            if (islandOpt.isEmpty()) {
                return "公共区域";
            }

            Island island = islandOpt.get();

            String name = island.getName();

            if (name == null || name.isBlank()) {
                return "岛屿 #" + island.getId();
            }

            return name;

        } catch (Throwable ignored) {
        }

        return "公共区域";
    }

    private String getPlayerRole(
            IslandManager islandManager,
            int chunkX,
            int chunkZ,
            UUID playerUuid
    ) {

        try {

            Optional<Island> islandOpt =
                    islandManager.getIslandAt(chunkX, chunkZ);

            if (islandOpt.isEmpty()) {
                islandOpt =
                        islandManager.getIslandAtMaxRange(chunkX, chunkZ);
            }

            if (islandOpt.isEmpty()) {
                return IslandPermissionLevel.VISITOR.getDisplayName();
            }

            Island island = islandOpt.get();

            IslandPermissionLevel role =
                    island.getMemberRole(playerUuid);

            if (role != IslandPermissionLevel.VISITOR) {
                return role.getDisplayName();
            }

            if (island.isCoop(playerUuid)) {
                return IslandPermissionLevel.COOP.getDisplayName();
            }

            return IslandPermissionLevel.VISITOR.getDisplayName();

        } catch (Throwable ignored) {
        }

        return IslandPermissionLevel.VISITOR.getDisplayName();
    }

    private String getPermissionLevelWeight(
            IslandManager islandManager,
            UUID playerUuid,
            String permName
    ) {

        try {

            Optional<Island> islandOpt =
                    islandManager.getIslandByPlayer(playerUuid);

            if (islandOpt.isEmpty()) {
                return "0";
            }

            Island island = islandOpt.get();

            IslandPermission permission =
                    IslandPermission.valueOf(permName.toUpperCase());

            Integer customLevel =
                    island.getPermissionMinLevel(permission);

            if (customLevel != null) {
                return String.valueOf(customLevel);
            }

            return String.valueOf(
                    IslandPermissionLevel.OWNER.getPermissionLevel()
            );

        } catch (Throwable ignored) {
        }

        return "0";
    }

    private String getPermissionRoleStatus(
            IslandManager islandManager,
            UUID playerUuid,
            String permName,
            String roleName
    ) {

        try {

            Optional<Island> islandOpt =
                    islandManager.getIslandByPlayer(playerUuid);

            if (islandOpt.isEmpty()) {
                return EMPTY;
            }

            Island island = islandOpt.get();

            IslandPermission permission =
                    IslandPermission.valueOf(permName.toUpperCase());

            IslandPermissionLevel role =
                    IslandPermissionLevel.valueOf(roleName.toUpperCase());

            boolean hasPerm =
                    island.hasPermission(role, permission);

            return (hasPerm ? "&a" : "&c")
                    + role.getDisplayName();

        } catch (Throwable ignored) {
        }

        return EMPTY;
    }

    private String getIslandSettingStatus(
            IslandManager islandManager,
            UUID playerUuid,
            String settingName
    ) {

        try {

            Optional<Island> islandOpt =
                    islandManager.getIslandByPlayer(playerUuid);

            if (islandOpt.isEmpty()) {
                return EMPTY;
            }

            Island island = islandOpt.get();

            IslandSetting setting =
                    IslandSetting.valueOf(settingName.toUpperCase());

            boolean enabled =
                    island.getSetting(setting);

            return enabled ? "&a是" : "&c否";

        } catch (Throwable ignored) {
        }

        return EMPTY;
    }
}