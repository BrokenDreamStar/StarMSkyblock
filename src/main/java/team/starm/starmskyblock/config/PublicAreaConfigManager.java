package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.setting.IslandSetting;

import java.util.EnumMap;
import java.util.Map;

/**
 * 公共区域权限与设置管理器（数据从 permissions.yml 的 public-area 章节读取）。
 * <p>
 * 定义空岛世界中所有非岛屿区域（虚空、岛屿间隙、世界出生点）的
 * 默认权限行为和设置开关。使用简单的 true/false 布尔值配置。
 * </p>
 */
public class PublicAreaConfigManager {

    private static final String CONFIG_PREFIX = "public-area";

    private final PermissionConfigManager permissionConfigManager;

    private final Map<IslandPermission, Boolean> permissionDefaults = new EnumMap<>(IslandPermission.class);
    private final Map<IslandSetting, Boolean> settingDefaults = new EnumMap<>(IslandSetting.class);

    public PublicAreaConfigManager(PermissionConfigManager permissionConfigManager) {
        this.permissionConfigManager = permissionConfigManager;
    }

    /** 加载公共区域权限与设置配置 */
    public void initialize() {
        loadConfig();
    }

    /** 从 permissions.yml 的 public-area 章节读取权限与设置默认值 */
    public void loadConfig() {
        FileConfiguration config = permissionConfigManager.getPermissionsConfig();
        if (config == null) {
            MessageUtil.consoleWarn("permissions.yml 尚未加载，无法读取 public-area 配置");
            return;
        }
        loadValues(config);
        MessageUtil.consolePrint("已加载公共区域权限与设置配置");
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

    /** 重载配置 */
    public void reload() {
        loadConfig();
    }
}