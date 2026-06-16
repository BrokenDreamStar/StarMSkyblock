package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.permission.IslandPermission;
import team.starm.starmskyblock.setting.IslandSetting;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * 公共区域权限与设置管理器（public-area.yml）。
 * <p>
 * 定义空岛世界中所有非岛屿区域（虚空、岛屿间隙、世界出生点）的
 * 默认权限行为和设置开关。使用简单的 true/false 布尔值配置。
 * </p>
 */
public class PublicAreaConfigManager {

    private final StarMSkyblock plugin;
    private FileConfiguration config;
    private File configFile;

    private boolean enabled;
    private final Map<IslandPermission, Boolean> permissionDefaults = new EnumMap<>(IslandPermission.class);
    private final Map<IslandSetting, Boolean> settingDefaults = new EnumMap<>(IslandSetting.class);

    public PublicAreaConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfig();
    }

    public void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "public-area.yml");

        if (!configFile.exists()) {
            plugin.saveResource("public-area.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        loadValues();

        MessageUtil.consolePrint("已加载公共区域权限与设置配置");
    }

    private void loadValues() {
        enabled = config.getBoolean("enabled", true);

        permissionDefaults.clear();
        for (IslandPermission permission : IslandPermission.values()) {
            String key = "permissions." + permission.name();
            boolean value = config.getBoolean(key, false);
            permissionDefaults.put(permission, value);
        }

        settingDefaults.clear();
        for (IslandSetting setting : IslandSetting.values()) {
            String key = "settings." + setting.name();
            boolean value = config.getBoolean(key, true);
            settingDefaults.put(setting, value);
        }
    }

    public boolean isEnabled() {
        return enabled;
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