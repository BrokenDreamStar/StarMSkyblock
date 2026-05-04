package team.starm.starmskyblock.permission.manager;

import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.island.IslandManager;
import team.starm.starmskyblock.permission.IslandPermissionManager;

/**
 * 管理权限管理器
 * 处理岛屿管理相关的权限检查
 */
public class ManagementPermissionManager extends IslandPermissionManager {

    public ManagementPermissionManager(IslandManager islandManager, ConfigManager configManager) {
        super(islandManager, configManager);
    }
}
