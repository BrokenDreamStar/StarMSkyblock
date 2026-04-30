package team.starm.starmskyblock.permission.manager;

import java.util.UUID;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

/**
 * 管理权限管理器
 * 处理岛屿管理相关的权限检查
 */
public class ManagementPermissionManager extends IslandPermissionManager {

    public ManagementPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 判断玩家是否具有管理成员的权限（所有者或具有邀请/踢出/设置权的角色）
     */
    public boolean canManageMembers(Island island, UUID uuid) {
        if (island == null)
            return true;
        if (island.getOwnerId().equals(uuid))
            return true;

        return hasPermission(island, uuid, IslandPermission.INVITE_MEMBER) ||
                hasPermission(island, uuid, IslandPermission.REMOVE_MEMBER) ||
                hasPermission(island, uuid, IslandPermission.SET_ROLE);
    }

    /**
     * 检查是否可以删除岛屿
     */
    public boolean canDeleteIsland(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.DELETE_ISLAND);
    }

    /**
     * 检查是否可以修改岛屿名称
     */
    public boolean canChangeName(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.CHANGE_NAME);
    }

    /**
     * 检查是否可以设置权限
     */
    public boolean canSetPermissions(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.SET_PERMISSIONS);
    }

    /**
     * 检查是否可以邀请成员
     */
    public boolean canInviteMember(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.INVITE_MEMBER);
    }

    /**
     * 检查是否可以移除成员
     */
    public boolean canRemoveMember(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.REMOVE_MEMBER);
    }

    /**
     * 检查是否可以设置成员角色
     */
    public boolean canSetRole(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.SET_ROLE);
    }

    /**
     * 检查是否可以邀请合作者
     */
    public boolean canInviteCoop(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.INVITE_COOP);
    }

    /**
     * 检查是否可以移除合作者
     */
    public boolean canRemoveCoop(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.REMOVE_COOP);
    }

    /**
     * 检查是否可以设置传送点
     */
    public boolean canSetHome(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.SET_HOME);
    }

    /**
     * 检查是否可以修改生物群系
     */
    public boolean canSetBiome(Island island, UUID uuid) {
        return hasPermission(island, uuid, IslandPermission.SET_BIOME);
    }
}