package team.starm.starmskyblock.permission.manager;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.BasePermissionManager;
import team.starm.starmskyblock.permission.IslandPermission;

import java.util.UUID;

/**
 * 管理权限管理器
 * <p>
 * 提供管理类权限的统一检查方法，覆盖以下操作：
 * 删除/重命名岛屿、修改权限/设置、邀请/移除成员、
 * 设置成员角色、邀请/移除合作者、设置传送点、设置生物群系。
 * </p>
 * <p>
 * 此类不包含事件监听器，管理类权限由指令处理器（CommandExecutor）在
 * 执行管理指令前主动调用 {lacksPermission} 方法进行检查。
 * </p>
 */
public class ManagementPermissionManager extends BasePermissionManager {

    public ManagementPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }

    /**
     * 检查玩家是否缺少指定的管理权限
     * <p>
     * 这是 {hasPermission} 的反向调用，返回 true 表示玩家<b>没有</b>权限。
     * 专门用于指令处理器中快速判断并拒绝无权限玩家，
     * 语义上比布尔取反值更清晰易读。
     * </p>
     *
     * @param island      玩家所在岛屿
     * @param playerUuid  玩家 UUID
     * @param permission  要检查的权限
     * @return true 表示玩家没有该权限
     */
    public static boolean lacksPermission(Island island, UUID playerUuid, IslandPermission permission) {
        return !BasePermissionManager.hasPermission(island, playerUuid, permission);
    }
}
