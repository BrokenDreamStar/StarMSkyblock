package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 岛屿生成时告示牌文字配置管理器
 */
public class SignConfigManager {

    private final StarMSkyblock plugin;
    private FileConfiguration signConfig;
    private File signFile;

    private boolean enabled;
    private List<String> lines;

    public SignConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadSignConfig();
    }

    public void loadSignConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        signFile = new File(plugin.getDataFolder(), "sign.yml");

        if (!signFile.exists()) {
            plugin.saveResource("sign.yml", false);
        }

        signConfig = YamlConfiguration.loadConfiguration(signFile);
        this.enabled = signConfig.getBoolean("enabled", true);
        this.lines = signConfig.getStringList("lines");
        if (this.lines == null) {
            this.lines = new ArrayList<>();
        }

        plugin.getLogger().info("§a[告示牌系统] 已加载岛屿告示牌配置");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getLines() {
        return new ArrayList<>(lines);
    }

    public void saveSignConfig() {
        if (signConfig != null && signFile != null) {
            try {
                signConfig.save(signFile);
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "保存告示牌配置文件失败", e);
            }
        }
    }

    public void reloadSignConfig() {
        loadSignConfig();
    }

    public FileConfiguration getSignConfig() {
        return signConfig;
    }
}
