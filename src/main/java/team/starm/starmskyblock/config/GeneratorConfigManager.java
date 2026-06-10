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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

    public record WeightedEntry(double cumulativeWeight, String material) {}

    public record GeneratorTier(int minLevel,
                                Map<String, Double> normal,
                                Map<String, Double> end,
                                Map<String, Double> nether,
                                List<WeightedEntry> normalEntries,
                                List<WeightedEntry> endEntries,
                                List<WeightedEntry> netherEntries) {

        public GeneratorTier(int minLevel,
                             Map<String, Double> normal,
                             Map<String, Double> end,
                             Map<String, Double> nether) {
            this(minLevel, normal, end, nether,
                    buildWeightedEntries(normal),
                    buildWeightedEntries(end),
                    buildWeightedEntries(nether));
        }

        private static List<WeightedEntry> buildWeightedEntries(Map<String, Double> rates) {
            if (rates.isEmpty()) return List.of();
            List<WeightedEntry> entries = new ArrayList<>();
            double cumulative = 0;
            for (Map.Entry<String, Double> e : rates.entrySet()) {
                cumulative += e.getValue();
                entries.add(new WeightedEntry(cumulative, e.getKey()));
            }
            return entries;
        }
    }

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

                    Map<String, Double> normal = readRates(tierSection, "normal");
                    Map<String, Double> end = readRates(tierSection, "end");
                    Map<String, Double> nether = readRates(tierSection, "nether");

                    tiers.put(minLevel, new GeneratorTier(minLevel, normal, end, nether));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (tiers.isEmpty()) {
            Map<String, Double> cobbleOnly = new LinkedHashMap<>();
            cobbleOnly.put("COBBLESTONE", 100.0);
            Map<String, Double> basaltOnly = new LinkedHashMap<>();
            basaltOnly.put("BASALT", 100.0);
            tiers.put(1, new GeneratorTier(1, cobbleOnly, cobbleOnly, basaltOnly));
        }

        MessageUtil.consolePrint("已加载刷石机配置");
    }

    private Map<String, Double> readRates(ConfigurationSection parent, String key) {
        Map<String, Double> result = new LinkedHashMap<>();
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section != null) {
            for (String material : section.getKeys(false)) {
                result.put(material, section.getDouble(material, 0));
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

    public int getMaxLevel() {
        if (tiers.isEmpty()) return 1;
        return tiers.lastKey();
    }

    public java.util.Optional<GeneratorTier> getNextTier(int generatorLevel) {
        Integer nextKey = tiers.higherKey(generatorLevel);
        if (nextKey == null) return java.util.Optional.empty();
        return java.util.Optional.of(tiers.get(nextKey));
    }

    public GeneratorTier getTier(int generatorLevel) {
        Map.Entry<Integer, GeneratorTier> entry = tiers.floorEntry(generatorLevel);
        if (entry == null) {
            return tiers.firstEntry().getValue();
        }
        return entry.getValue();
    }
}
