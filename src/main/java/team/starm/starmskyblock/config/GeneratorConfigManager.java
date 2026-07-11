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
import java.util.Optional;

/**
 * 刷石机生成配置管理器（generator.yml）。
 * <p>
 * 加载按最低等级分层的刷石机配方（{@link GeneratorTier}），支持主世界/下界/末地
 * 三套独立的方块产出权重。每个 tier 预构建累积权重表（{@link WeightedEntry}），
 * 供运行时按随机数二分查找 O(log n) 落点。
 */
public class GeneratorConfigManager {

    private final StarMSkyblock plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private boolean deepslateEnabled;
    private int deepslateYThreshold;
    private TreeMap<Integer, GeneratorTier> tiers;

    /** 累积权重条目 -- 将原始权重转为前缀和,运行时按随机数二分查找落点。 */
    public record WeightedEntry(double cumulativeWeight, String material) {}

    /**
     * 单个等级档位的刷石机配置。
     * <p>每个环境(normal/end/nether)维护一份材料->权重映射及预构建的累积权重表。
     */
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

    /** 加载 generator.yml，若文件不存在则从 jar 释放默认配置。 */
    public void initialize() {
        configFile = new File(plugin.getDataFolder(), "generator.yml");
        if (!configFile.exists()) {
            plugin.saveResource("generator.yml", false);
        }
        reload();
    }

    /** 重载配置并重建各档累积权重表。 */
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

    /**
     * 返回高于当前等级的下一档配置；已是最高档时返回 {@link Optional#empty()}。
     *
     * @param generatorLevel 当前刷石机等级
     * @return 下一档配置，或 empty
     */
    public Optional<GeneratorTier> getNextTier(int generatorLevel) {
        Integer nextKey = tiers.higherKey(generatorLevel);
        if (nextKey == null) return Optional.empty();
        return Optional.of(tiers.get(nextKey));
    }

    /**
     * 返回当前刷石机等级对应的档位 -- 取 {@code floorEntry}(不超过该等级的最大档)，
     * 等级低于首档时回退到首档。
     */
    public GeneratorTier getTier(int generatorLevel) {
        Map.Entry<Integer, GeneratorTier> entry = tiers.floorEntry(generatorLevel);
        if (entry == null) {
            return tiers.firstEntry().getValue();
        }
        return entry.getValue();
    }
}
