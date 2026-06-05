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
import java.util.List;
import java.util.Optional;

public class UpgradeConfigManager {

    private final StarMSkyblock plugin;
    private File configFile;
    private FileConfiguration config;

    private List<RadiusUpgrade> radiusUpgrades;
    private List<GeneratorUpgrade> generatorUpgrades;

    public record RadiusUpgrade(int tier, int radius, double money) {}
    public record GeneratorUpgrade(int tier, int generatorLevel, double money) {}

    public UpgradeConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        configFile = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!configFile.exists()) {
            plugin.saveResource("upgrades.yml", false);
        }
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource("upgrades.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        radiusUpgrades = new ArrayList<>();
        ConfigurationSection radiusSection = config.getConfigurationSection("island-radius-upgrades");
        if (radiusSection != null) {
            for (String key : radiusSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    ConfigurationSection entry = radiusSection.getConfigurationSection(key);
                    if (entry == null) continue;
                    int targetRadius = entry.getInt("radius");
                    double money = entry.getDouble("money");
                    radiusUpgrades.add(new RadiusUpgrade(tier, targetRadius, money));
                } catch (NumberFormatException ignored) {}
            }
        }
        Collections.sort(radiusUpgrades, (a, b) -> Integer.compare(a.tier(), b.tier()));

        generatorUpgrades = new ArrayList<>();
        ConfigurationSection genSection = config.getConfigurationSection("generator-upgrades");
        if (genSection != null) {
            for (String key : genSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    ConfigurationSection entry = genSection.getConfigurationSection(key);
                    if (entry == null) continue;
                    int targetLevel = entry.getInt("generator-level");
                    double money = entry.getDouble("money");
                    generatorUpgrades.add(new GeneratorUpgrade(tier, targetLevel, money));
                } catch (NumberFormatException ignored) {}
            }
        }
        Collections.sort(generatorUpgrades, (a, b) -> Integer.compare(a.tier(), b.tier()));

        MessageUtil.consolePrint("已加载升级配置");
    }

    public Optional<RadiusUpgrade> getNextRadiusUpgrade(int currentRadius) {
        for (RadiusUpgrade upgrade : radiusUpgrades) {
            if (upgrade.radius() > currentRadius) {
                return Optional.of(upgrade);
            }
        }
        return Optional.empty();
    }

    public Optional<GeneratorUpgrade> getNextGeneratorUpgrade(int currentLevel) {
        for (GeneratorUpgrade upgrade : generatorUpgrades) {
            if (upgrade.generatorLevel() > currentLevel) {
                return Optional.of(upgrade);
            }
        }
        return Optional.empty();
    }

    public List<RadiusUpgrade> getRadiusUpgrades() {
        return Collections.unmodifiableList(radiusUpgrades);
    }

    public List<GeneratorUpgrade> getGeneratorUpgrades() {
        return Collections.unmodifiableList(generatorUpgrades);
    }
}
