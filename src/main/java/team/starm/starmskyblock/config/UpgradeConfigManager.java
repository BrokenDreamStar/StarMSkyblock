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

/**
 * 岛屿升级配置管理器（upgrades.yml）。
 * <p>
 * 加载两条升级路径的阶梯配置，供 Vault 经济扣费升级使用：
 * <ul>
 *   <li>岛屿半径升级（{@link RadiusUpgrade}）</li>
 *   <li>刷石机等级升级（{@link GeneratorUpgrade}）</li>
 * </ul>
 * 升级档位按 tier 升序排列，{@code getNext*Upgrade} 返回首个超过当前值的档位。
 */
public class UpgradeConfigManager {

    private final StarMSkyblock plugin;
    private File configFile;
    private FileConfiguration config;

    private List<RadiusUpgrade> radiusUpgrades;
    private List<GeneratorUpgrade> generatorUpgrades;

    /** 岛屿半径升级档位：达到该 tier 后岛屿半径扩展为 {@code radius}，花费 {@code money}。 */
    public record RadiusUpgrade(int tier, int radius, double money) {}
    /** 刷石机等级升级档位：达到该 tier 后刷石机等级提升为 {@code generatorLevel}，花费 {@code money}。 */
    public record GeneratorUpgrade(int tier, int generatorLevel, double money) {}

    public UpgradeConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /** 加载 upgrades.yml，若文件不存在则从 jar 释放默认配置。 */
    public void initialize() {
        configFile = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!configFile.exists()) {
            plugin.saveResource("upgrades.yml", false);
        }
        reload();
    }

    /** 重载配置并按 tier 升序重排两条升级路径。 */
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

    /**
     * 返回首个半径超过当前值的升级档位。
     *
     * @param currentRadius 当前岛屿半径
     * @return 下一档升级，已满级时返回 empty
     */
    public Optional<RadiusUpgrade> getNextRadiusUpgrade(int currentRadius) {
        for (RadiusUpgrade upgrade : radiusUpgrades) {
            if (upgrade.radius() > currentRadius) {
                return Optional.of(upgrade);
            }
        }
        return Optional.empty();
    }

    /**
     * 返回首个等级超过当前值的刷石机升级档位。
     *
     * @param currentLevel 当前刷石机等级
     * @return 下一档升级，已满级时返回 empty
     */
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
