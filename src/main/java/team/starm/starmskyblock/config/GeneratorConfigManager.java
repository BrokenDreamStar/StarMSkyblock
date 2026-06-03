package team.starm.starmskyblock.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class GeneratorConfigManager {

    private final StarMSkyblock plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private boolean deepslateEnabled;
    private int deepslateYThreshold;
    private TreeMap<Integer, GeneratorTier> tiers;

    public record GeneratorTier(int minLevel,
                                Map<String, Integer> normal,
                                Map<String, Integer> end,
                                Map<String, Integer> nether) {}

    public GeneratorConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        configFile = new File(plugin.getDataFolder(), "generator.yml");
        if (!configFile.exists()) {
            plugin.saveResource("generator.yml", false);
        }
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource("generator.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        this.enabled = config.getBoolean("enabled", true);
        this.deepslateEnabled = config.getBoolean("deepslate.enabled", true);
        this.deepslateYThreshold = config.getInt("deepslate.y-threshold", 0);
        this.tiers = new TreeMap<>();

        ConfigurationSection levelsSection = config.getConfigurationSection("levels");
        if (levelsSection != null) {
            for (String levelKey : levelsSection.getKeys(false)) {
                try {
                    int minLevel = Integer.parseInt(levelKey);
                    ConfigurationSection tierSection = levelsSection.getConfigurationSection(levelKey);
                    if (tierSection == null) continue;

                    Map<String, Integer> normal = readRates(tierSection, "normal");
                    Map<String, Integer> end = readRates(tierSection, "end");
                    Map<String, Integer> nether = readRates(tierSection, "nether");

                    tiers.put(minLevel, new GeneratorTier(minLevel, normal, end, nether));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (tiers.isEmpty()) {
            Map<String, Integer> cobbleOnly = new LinkedHashMap<>();
            cobbleOnly.put("COBBLESTONE", 100);
            Map<String, Integer> basaltOnly = new LinkedHashMap<>();
            basaltOnly.put("BASALT", 100);
            tiers.put(1, new GeneratorTier(1, cobbleOnly, cobbleOnly, basaltOnly));
        }

        MessageUtil.consolePrint("刷石机配置已加载，共 " + tiers.size() + " 个等级阶");
    }

    private Map<String, Integer> readRates(ConfigurationSection parent, String key) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section != null) {
            for (String material : section.getKeys(false)) {
                result.put(material, section.getInt(material, 0));
            }
        }
        return result;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDeepslateEnabled() {
        return deepslateEnabled;
    }

    public int getDeepslateYThreshold() {
        return deepslateYThreshold;
    }

    public GeneratorTier getTier(int generatorLevel) {
        Map.Entry<Integer, GeneratorTier> entry = tiers.floorEntry(generatorLevel);
        if (entry == null) {
            return tiers.firstEntry().getValue();
        }
        return entry.getValue();
    }
}
