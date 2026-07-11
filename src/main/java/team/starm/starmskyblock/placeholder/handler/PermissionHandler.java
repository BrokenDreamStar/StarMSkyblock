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

/**
 * 权限占位符处理器。
 * <p>
 * 支持三类占位符：
 * <ul>
 *   <li>{@code permission_<perm>_level_weight} -- 某权限所需的最低身份等级</li>
 *   <li>{@code permission_<perm>_level_<role>} -- 某身份是否拥有该权限</li>
 *   <li>{@code haspermission_<perm>} -- 当前玩家是否拥有该权限</li>
 * </ul>
 */
public class PermissionHandler {

    /** 权限状态占位符前缀 */
    public static final String PREFIX = "permission_";
    /** 权限判定占位符前缀（haspermission_） */
    public static final String HAS_PERMISSION_PREFIX = "haspermission_";

    private static final String SUFFIX = "_level_weight";
    private static final String LEVEL_SEPARATOR = "_level_";

    private final StarMSkyblock plugin;

    public PermissionHandler(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理权限相关占位符请求。
     *
     * @param player 请求占位符的玩家
     * @param params 占位符参数
     * @return 渲染结果，无法识别时返回 null
     */
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

    /** 返回玩家所属岛屿中某权限的最低身份等级权重，无自定义时返回 OWNER 等级。 */
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

    /** 返回某身份是否拥有该权限的渲染结果（带颜色前缀）。 */
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
