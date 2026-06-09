package team.starm.starmskyblock.placeholder.handler;

import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.permission.IslandPermissionLevel;

import team.starm.starmskyblock.message.MessageUtil;
import java.util.Optional;
import java.util.UUID;

public class PermissionHandler {

    public static final String PREFIX = "permission_";
    public static final String HAS_PERMISSION_PREFIX = "haspermission_";

    private static final String SUFFIX = "_level_weight";
    private static final String LEVEL_SEPARATOR = "_level_";

    private final StarMSkyblock plugin;

    public PermissionHandler(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public String handle(Player player, String params) {

        try {

            IslandManager islandManager = plugin.getIslandManager();

            if (params.regionMatches(
                    true,
                    0,
                    PREFIX,
                    0,
                    PREFIX.length()
            )) {

                if (params.regionMatches(
                        true,
                        params.length() - SUFFIX.length(),
                        SUFFIX,
                        0,
                        SUFFIX.length()
                )) {

                    String permName =
                            params.substring(
                                    PREFIX.length(),
                                    params.length() - SUFFIX.length()
                            );

                    return getPermissionLevelWeight(
                            islandManager,
                            player.getUniqueId(),
                            permName
                    );
                }

                String rest = params.substring(PREFIX.length());

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
            MessageUtil.consoleError("处理权限占位符时发生错误", throwable);
        }

        return null;
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
                return null;
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

        return null;
    }
}
