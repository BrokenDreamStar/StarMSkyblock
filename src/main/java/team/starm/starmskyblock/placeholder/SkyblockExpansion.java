package team.starm.starmskyblock.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.setting.IslandSetting;
import team.starm.starmskyblock.util.SkullTextureCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * PlaceholderAPI 扩展，提供 %starmskyblock_*% 变量占位符。
 * 子命令格式：
 *   %starmskyblock_island%           — 当前区块所在岛屿名称
 *   %starmskyblock_role%             — 玩家在当前岛屿的角色
 *   %starmskyblock_permission_{PERM}_level_weight%  — 某权限的最低等级
 *   %starmskyblock_permission_{PERM}_level_{ROLE}% — 某角色是否有某权限
 *   %starmskyblock_islandsettings_{SETTING}%       — 某设置开关状态
 */
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

    private static final String PERMISSION_PREFIX = "permission_";
    private static final String PERMISSION_SUFFIX = "_level_weight";
    private static final String LEVEL_SEPARATOR = "_level_";
    private static final String SETTINGS_PREFIX = "islandsettings_";
    private static final String HAS_PERMISSION_PREFIX = "haspermission_";
    private static final String ISLAND_LIST_PREFIX = "island_list_";
    private static final String ISLAND_LIST_SIZE = "island_list_size";
    private static final String ISLAND_LIST_MAX_PAGE = "island_list_max_page";
    private static final String ISLAND_LIST_IS_LAST_PAGE = "island_list_is_last_page";
    private static final Pattern ISLAND_LIST_ITEM_PATTERN = Pattern.compile("island_list_(\\d+)(?:_(\\w+))?", Pattern.CASE_INSENSITIVE);

    private final AtomicInteger papiCallCount = new AtomicInteger(0);

    public int getPapiCallCount() {
        return papiCallCount.get();
    }

    public void resetPapiCallCount() {
        papiCallCount.set(0);
    }

    private volatile List<Island> sortedIslandsCache;
    private volatile long sortedIslandsCacheTime;

    private final Map<UUID, Integer> playerListPages = new HashMap<>();
    private static final int ISLANDS_PER_PAGE = 28;

    public void setPlayerPage(Player player, int page) {
        if (page < 1) page = 1;
        playerListPages.put(player.getUniqueId(), page);
    }

    public int getPlayerPage(Player player) {
        return playerListPages.getOrDefault(player.getUniqueId(), 1);
    }

    public void resetPlayerPage(Player player) {
        playerListPages.remove(player.getUniqueId());
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        papiCallCount.incrementAndGet();

        if (params.equalsIgnoreCase("call_count")) {
            return String.valueOf(papiCallCount.get());
        }

        if (params.equalsIgnoreCase("call_count_reset")) {
            return String.valueOf(papiCallCount.getAndSet(0));
        }

        if (player == null) return "";

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
            return String.valueOf(Math.max(1, (int) Math.ceil((double) getSortedIslands().size() / ISLANDS_PER_PAGE)));
        }

        if (params.equalsIgnoreCase(ISLAND_LIST_IS_LAST_PAGE)) {
            int currentPage = getPlayerPage(player);
            int maxPage = Math.max(1, (int) Math.ceil((double) getSortedIslands().size() / ISLANDS_PER_PAGE));
            return currentPage >= maxPage ? "true" : "false";
        }

        if (params.equalsIgnoreCase("creationtime")) {
            Optional<Island> islandOpt = islandManager.getIslandByPlayer(player.getUniqueId());
            if (islandOpt.isEmpty()) return "";
            String time = islandOpt.get().getCreatedAt();
            return time != null ? time : "";
        }

        if (params.regionMatches(true, 0, ISLAND_LIST_PREFIX, 0, ISLAND_LIST_PREFIX.length())) {
            return handleIslandListItem(player, params);
        }

        if (params.regionMatches(true, 0, SETTINGS_PREFIX, 0, SETTINGS_PREFIX.length())) {
            String settingName = params.substring(SETTINGS_PREFIX.length());
            return getIslandSettingStatus(islandManager, player.getUniqueId(), settingName);
        }

        if (params.regionMatches(true, 0, PERMISSION_PREFIX, 0, PERMISSION_PREFIX.length())) {
            if (params.regionMatches(true, params.length() - PERMISSION_SUFFIX.length(), PERMISSION_SUFFIX, 0, PERMISSION_SUFFIX.length())) {
                String permName = params.substring(PERMISSION_PREFIX.length(), params.length() - PERMISSION_SUFFIX.length());
                return getPermissionLevelWeight(islandManager, player.getUniqueId(), permName);
            }

            // permission_{PERM}_level_{ROLE}
            String rest = params.substring(PERMISSION_PREFIX.length());
            int separatorIdx = rest.lastIndexOf(LEVEL_SEPARATOR);
            if (separatorIdx != -1) {
                String permName = rest.substring(0, separatorIdx);
                String roleName = rest.substring(separatorIdx + LEVEL_SEPARATOR.length());
                return getPermissionRoleStatus(islandManager, player.getUniqueId(), permName, roleName);
            }
        }

        if (params.regionMatches(true, 0, HAS_PERMISSION_PREFIX, 0, HAS_PERMISSION_PREFIX.length())) {
            String permName = params.substring(HAS_PERMISSION_PREFIX.length());
            if (permName.isEmpty()) return null;
            try {
                IslandPermission permission = IslandPermission.valueOf(permName.toUpperCase());
                Optional<Island> islandOpt = islandManager.getIslandByPlayer(player.getUniqueId());
                if (islandOpt.isPresent()) {
                    return islandOpt.get().hasPermission(player.getUniqueId(), permission) ? "true" : "false";
                }
            } catch (IllegalArgumentException ignored) {}
            return "false";
        }

        return null;
    }

    private String getIslandName(IslandManager islandManager, int chunkX, int chunkZ) {
        Optional<Island> islandOpt = islandManager.getIslandAt(chunkX, chunkZ);
        if (islandOpt.isEmpty()) {
            // 当前半径内找不到，尝试在最大半径范围查找（未解锁区域）
            islandOpt = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
        }
        if (islandOpt.isEmpty()) {
            return "公共区域";
        }

        Island island = islandOpt.get();
        String name = island.getName();
        if (name == null || name.isEmpty()) {
            return "岛屿 #" + island.getId();
        }
        return name;
    }

    private String getPlayerRole(IslandManager islandManager, int chunkX, int chunkZ, UUID playerUuid) {
        Optional<Island> islandOpt = islandManager.getIslandAt(chunkX, chunkZ);
        if (islandOpt.isEmpty()) {
            // 当前半径内找不到，尝试在最大半径范围查找（未解锁区域）
            islandOpt = islandManager.getIslandAtMaxRange(chunkX, chunkZ);
        }
        if (islandOpt.isEmpty()) {
            return IslandPermissionLevel.VISITOR.getDisplayName();
        }

        Island island = islandOpt.get();

        IslandPermissionLevel role = island.getMemberRole(playerUuid);
        if (role != IslandPermissionLevel.VISITOR) {
            return role.getDisplayName();
        }

        if (island.isCoop(playerUuid)) {
            return IslandPermissionLevel.COOP.getDisplayName();
        }

        return IslandPermissionLevel.VISITOR.getDisplayName();
    }

    private String getPermissionLevelWeight(IslandManager islandManager, UUID playerUuid, String permName) {
        Optional<Island> islandOpt = islandManager.getIslandByPlayer(playerUuid);
        if (islandOpt.isEmpty()) {
            return "0";
        }

        Island island = islandOpt.get();

        IslandPermission permission;
        try {
            permission = IslandPermission.valueOf(permName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        Integer customLevel = island.getPermissionMinLevel(permission);
        if (customLevel != null) {
            return String.valueOf(customLevel);
        }

        return String.valueOf(IslandPermissionLevel.OWNER.getPermissionLevel());
    }

    private String getPermissionRoleStatus(IslandManager islandManager, UUID playerUuid, String permName, String roleName) {
        Optional<Island> islandOpt = islandManager.getIslandByPlayer(playerUuid);
        if (islandOpt.isEmpty()) {
            return null;
        }

        Island island = islandOpt.get();

        IslandPermission permission;
        try {
            permission = IslandPermission.valueOf(permName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        IslandPermissionLevel role;
        try {
            role = IslandPermissionLevel.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        boolean hasPerm = island.hasPermission(role, permission);
        return (hasPerm ? "&a" : "&c") + role.getDisplayName();
    }

    private String getIslandSettingStatus(IslandManager islandManager, UUID playerUuid, String settingName) {
        Optional<Island> islandOpt = islandManager.getIslandByPlayer(playerUuid);
        if (islandOpt.isEmpty()) {
            return null;
        }

        Island island = islandOpt.get();

        IslandSetting setting;
        try {
            setting = IslandSetting.valueOf(settingName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        boolean enabled = island.getSetting(setting);
        return enabled ? "&a是" : "&c否";
    }

    private List<Island> getSortedIslands() {
        long now = System.currentTimeMillis();
        if (sortedIslandsCache != null && now - sortedIslandsCacheTime < 1000) {
            return sortedIslandsCache;
        }
        List<Island> list = new ArrayList<>(plugin.getIslandManager().getAllIslands());
        list.sort((a, b) -> {
            int levelCompare = Integer.compare(b.getLevel(), a.getLevel());
            if (levelCompare != 0) return levelCompare;
            return Integer.compare(a.getId(), b.getId());
        });
        sortedIslandsCache = list;
        sortedIslandsCacheTime = now;
        return list;
    }

    private String getOwnerName(Island island) {
        Optional<String> name = plugin.getSqliteManager().getPlayerName(island.getOwnerId());
        if (name.isPresent()) return name.get();
        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(island.getOwnerId());
        String playerName = player.getName();
        return playerName != null ? playerName : "未知";
    }

    private String handleIslandListItem(Player player, String params) {
        var matcher = ISLAND_LIST_ITEM_PATTERN.matcher(params);
        if (!matcher.matches()) return null;

        int slotNumber;
        try {
            slotNumber = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }

        int page = getPlayerPage(player);
        int actualIndex = (page - 1) * ISLANDS_PER_PAGE + slotNumber - 1;

        List<Island> sorted = getSortedIslands();
        if (actualIndex < 0 || actualIndex >= sorted.size()) return "NONE";

        Island island = sorted.get(actualIndex);
        String field = matcher.group(2);

        if (field == null || field.equalsIgnoreCase("name")) {
            String islandName = island.getName();
            if (islandName == null || islandName.isEmpty()) {
                return "岛屿 #" + island.getId();
            }
            return islandName;
        }

        return switch (field.toLowerCase()) {
            case "id" -> String.valueOf(island.getId());
            case "owner" -> getOwnerName(island);
            case "level" -> String.valueOf(island.getLevel());
            case "members" -> String.valueOf(island.getMembers().size() + 1);
            case "owner_head" -> {
                String texture = SkullTextureCache.getTexture(island.getOwnerId());
                yield texture != null ? texture : "";
            }
            default -> null;
        };
    }
}
