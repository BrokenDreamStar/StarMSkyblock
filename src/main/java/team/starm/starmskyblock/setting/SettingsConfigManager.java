package team.starm.starmskyblock.setting;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;

import java.io.File;
import java.io.IOException;

public class SettingsConfigManager {

    private final StarMSkyblock plugin;
    private FileConfiguration settingsConfig;
    private File settingsFile;

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
        plugin.getLogger().info("§a[设置系统] 已加载岛屿默认设置配置");
    }

    /**
     * 从 settings.yml 读取默认值并返回 IslandSettings 实例
     */
    public IslandSettings getDefaultSettings() {
        IslandSettings settings = new IslandSettings();

        for (String key : IslandSettings.getSettingKeys()) {
            boolean defaultValue = settingsConfig.getBoolean(key, true);
            settings.setByKey(key, defaultValue);
        }

        return settings;
    }

    public void saveSettingsConfig() {
        if (settingsConfig != null && settingsFile != null) {
            try {
                settingsConfig.save(settingsFile);
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "保存设置配置文件失败", e);
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
