package team.starm.starmskyblock.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.io.File;

/**
 * AuraSkills 对岛屿等级的贡献配置管理器（合并于 level.yml）。
 * <p>
 * 从 {@code level.yml} 的 {@code skill-contribution} 段加载配置，提供：
 * <ul>
 *   <li>是否启用加成（{@code isEnabled()}）</li>
 *   <li>PowerLevel 转换系数（{@code getCoefficient()}）</li>
 *   <li>最大加成等级上限（{@code getMaxBonusLevel()}）</li>
 * </ul>
 */
public class AuraSkillsContributionConfig {

    private static final String FILE_NAME = "level.yml";
    private static final String SECTION_NAME = "skill-contribution";

    private final StarMSkyblock plugin;
    private final File configFile;

    private String type = "mcmmo";
    private boolean enabled = true;
    private double coefficient = 45.0;
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
        ConfigurationSection section = config.getConfigurationSection(SECTION_NAME);

        if (section == null) {
            MessageUtil.consoleWarn("level.yml 缺少 '" + SECTION_NAME + "' 段，将使用默认值");
            return;
        }

        this.type = section.getString("type", "mcmmo");
        this.enabled = section.getBoolean("enabled", true);
        this.coefficient = section.getDouble("coefficient", 45.0);
        this.maxBonusLevel = section.getInt("max-bonus-level", 0);

        if (coefficient <= 0) {
            MessageUtil.consoleWarn("level.yml 中 skill-contribution.coefficient 必须大于 0，已重置为默认值 45.0");
            this.coefficient = 45.0;
        }

        if (enabled) {
            MessageUtil.consolePrint("检测到技能等级岛屿等级额外经验来源功能已启用（类型: " + type + "）");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getType() {
        return type;
    }

    public double getCoefficient() {
        return coefficient;
    }

    public int getMaxBonusLevel() {
        return maxBonusLevel;
    }
}