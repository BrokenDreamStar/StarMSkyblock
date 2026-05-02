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
}
