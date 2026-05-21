package team.starm.starmskyblock.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;
import team.starm.starmskyblock.setting.IslandSetting;

import java.util.Optional;
import java.util.UUID;

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

    @Override
    public String onPlaceholderRequest(Player player, String params) {
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

        Integer allLevel = island.getPermissionMinLevel(IslandPermission.ALL);
        if (allLevel != null) {
            return String.valueOf(allLevel);
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
}
