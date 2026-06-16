package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.io.File;

/**
 * AuraSkills 对岛屿等级的贡献配置管理器。
 * <p>
 * 负责加载 {@code auraskills-contribution.yml}，提供：
 * <ul>
 *   <li>是否启用加成（{@code isEnabled()}）</li>
 *   <li>PowerLevel 转换系数（{@code getCoefficient()}）</li>
 *   <li>最大加成等级上限（{@code getMaxBonusLevel()}）</li>
 * </ul>
 */
public class AuraSkillsContributionConfig {

    private static final String FILE_NAME = "auraskills-contribution.yml";

    private final StarMSkyblock plugin;
    private final File configFile;

    private boolean enabled = true;
    private double coefficient = 20.0;
    private int maxBonusLevel = 0;

    public AuraSkillsContributionConfig(StarMSkyblock plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /**
     * 加载/重载配置文件
     */
    public void initialize() {
        if (!configFile.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        reload();
    }

    /**
     * 重载配置
     */
    public void reload() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = config.getBoolean("enabled", true);
        this.coefficient = config.getDouble("coefficient", 100.0);
        this.maxBonusLevel = config.getInt("max-bonus-level", 0);

        if (coefficient <= 0) {
            MessageUtil.consoleWarn("auraskills-contribution.yml 中 coefficient 必须大于 0，已重置为默认值 100.0");
            this.coefficient = 100.0;
        }

        MessageUtil.consolePrint("检测到AuraSkills 岛屿等级额外经验来源功能已启用");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getCoefficient() {
        return coefficient;
    }

    public int getMaxBonusLevel() {
        return maxBonusLevel;
    }
}