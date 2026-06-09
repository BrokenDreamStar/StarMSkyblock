package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.setting.IslandSetting;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * 岛屿默认设置管理器（settings.yml）。
 * 管理每个 IslandSetting 开关的默认值，新建岛屿时从此处读取初始配置。
 */
public class SettingsConfigManager {

    private final StarMSkyblock plugin;
    private FileConfiguration settingsConfig;
    private File settingsFile;

    private final Map<IslandSetting, Boolean> defaultSettings = new EnumMap<>(IslandSetting.class);

    public SettingsConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadSettingsConfig();
    }

    public void loadSettingsConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        settingsFile = new File(plugin.getDataFolder(), "settings.yml");

        if (!settingsFile.exists()) {
            plugin.saveResource("settings.yml", false);
        }

        settingsConfig = YamlConfiguration.loadConfiguration(settingsFile);
        loadDefaultSettings();

        MessageUtil.consolePrint("已加载岛屿默认设置配置");
    }

    private void loadDefaultSettings() {
        defaultSettings.clear();
        for (IslandSetting setting : IslandSetting.values()) {
            String key = setting.getConfigKey();
            boolean defaultValue = settingsConfig.getBoolean(key, true);
            defaultSettings.put(setting, defaultValue);
        }
    }

    public Map<IslandSetting, Boolean> getDefaultSettings() {
        return new EnumMap<>(defaultSettings);
    }

    public boolean getDefaultSetting(IslandSetting setting) {
        return defaultSettings.getOrDefault(setting, true);
    }

    public void saveSettingsConfig() {
        if (settingsConfig != null && settingsFile != null) {
            try {
                settingsConfig.save(settingsFile);
            } catch (IOException e) {
                MessageUtil.consoleError("保存设置配置文件失败", e);
            }
        }
    }

    public void reloadSettingsConfig() {
        loadSettingsConfig();
    }

    public FileConfiguration getSettingsConfig() {
        return settingsConfig;
    }
}
