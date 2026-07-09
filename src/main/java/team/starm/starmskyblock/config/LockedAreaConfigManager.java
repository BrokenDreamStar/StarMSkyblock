package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.setting.IslandSetting;

import java.util.EnumMap;
import java.util.Map;

/**
 * 岛屿未解锁区域权限与设置管理器（数据从 permissions.yml 的 locked-area 章节读取）。
 * <p>
 * 定义空岛世界中已归属岛屿但尚未解锁（未扩展半径）区域的
 * 默认权限行为和设置开关。使用简单的 true/false 布尔值配置。
 * </p>
 */
public class LockedAreaConfigManager {

    private static final String CONFIG_PREFIX = "locked-area";

    private final PermissionConfigManager permissionConfigManager;

    private final Map<IslandPermission, Boolean> permissionDefaults = new EnumMap<>(IslandPermission.class);
    private final Map<IslandSetting, Boolean> settingDefaults = new EnumMap<>(IslandSetting.class);

    public LockedAreaConfigManager(PermissionConfigManager permissionConfigManager) {
        this.permissionConfigManager = permissionConfigManager;
    }

    public void initialize() {
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = permissionConfigManager.getPermissionsConfig();
        if (config == null) {
            MessageUtil.consoleWarn("permissions.yml 尚未加载，无法读取 locked-area 配置");
            return;
        }
        loadValues(config);
        MessageUtil.consolePrint("已加载岛屿未解锁区域权限与设置配置");
    }

    private void loadValues(FileConfiguration config) {
        permissionDefaults.clear();
        for (IslandPermission permission : IslandPermission.values()) {
            String key = CONFIG_PREFIX + ".permissions." + permission.name();
            boolean value = config.getBoolean(key, false);
            permissionDefaults.put(permission, value);
        }

        settingDefaults.clear();
        for (IslandSetting setting : IslandSetting.values()) {
            String key = CONFIG_PREFIX + ".settings." + setting.name();
            boolean value = config.getBoolean(key, true);
            settingDefaults.put(setting, value);
        }
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean getPermission(IslandPermission permission) {
        return permissionDefaults.getOrDefault(permission, false);
    }

    public boolean getSetting(IslandSetting setting) {
        return settingDefaults.getOrDefault(setting, true);
    }

    public void reload() {
        loadConfig();
    }
}