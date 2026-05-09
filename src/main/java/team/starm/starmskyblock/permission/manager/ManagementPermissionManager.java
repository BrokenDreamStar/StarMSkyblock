package team.starm.starmskyblock.permission.manager;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

import java.util.UUID;

/**
 * 管理权限管理器
 * 处理 DELETE_ISLAND、RENAME_ISLAND、EDIT_PERMISSIONS、EDIT_SETTINGS、
 * INVITE_MEMBER、REMOVE_MEMBER、SET_ROLE、INVITE_COOP、REMOVE_COOP、SET_HOME、SET_BIOME
 * 等管理类权限的统一检查
 */
public class ManagementPermissionManager extends BasePermissionManager {

    public ManagementPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 检查玩家是否缺少指定的管理权限（返回 true 表示没有权限）
     */
    public static boolean lacksPermission(Island island, UUID playerUuid, IslandPermission permission) {
        return !BasePermissionManager.hasPermission(island, playerUuid, permission);
    }
}
