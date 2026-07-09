package team.starm.starmskyblock.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.message.MessageUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 岛屿等级 —— 方块经验值与阈值配置管理器。
 * <p>
 * 负责加载 {@code level.yml}，提供：
 * <ul>
 *   <li>每种方块的经验值（{@code getExperience(Material)}）</li>
 *   <li>每种方块的最大计数阈值（{@code getLimit(Material)}）</li>
 *   <li>等级计算公式（{@code getLevelFormula()}）</li>
 * </ul>
 */
public class ExperienceConfig {

    private static final String FILE_NAME = "level.yml";

    private final StarMSkyblock plugin;
    private final File configFile;

    private Map<Material, Double> experienceValues = new HashMap<>();
    private Map<Material, Long> blockLimits = new HashMap<>();
    private String levelFormula = "{experience} / 100";
    private double levelExpBase = 100.0;
    private double levelExpPower = 0.0;
    private boolean diminishingEnabled = false;
    private double diminishingDecay = 0.001;
    private double diminishingMinimum = 1.0;
    private boolean baselineEnabled = true;

    public ExperienceConfig(StarMSkyblock plugin) {
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

        // 加载方块经验值
        Map<Material, Double> values = new HashMap<>();
        ConfigurationSection blocksSection = config.getConfigurationSection("blocks");
        if (blocksSection != null) {
            for (String key : blocksSection.getKeys(false)) {
                // 跳过非方块的结构性键（如 limits、diminishing）
                if ("limits".equals(key) || "diminishing".equals(key)) {
                    continue;
                }
                Material material = Material.getMaterial(key.toUpperCase());
                if (material == null) {
                    MessageUtil.consoleWarn("level.yml 中存在未知的方块类型: " + key);
                    continue;
                }
                double value = blocksSection.getDouble(key);
                values.put(material, value);
            }
        } else {
            MessageUtil.consoleWarn("level.yml 缺少 'blocks' 段");
        }
        this.experienceValues = values;

        // 加载方块阈值（兼容旧版：之前 limits 被错误地嵌套在 blocks 下）
        Map<Material, Long> limits = new HashMap<>();
        ConfigurationSection limitsSection = config.getConfigurationSection("limits");
        if (limitsSection == null) {
            limitsSection = config.getConfigurationSection("blocks.limits");
        }
        if (limitsSection != null) {
            for (String key : limitsSection.getKeys(false)) {
                Material material = Material.getMaterial(key.toUpperCase());
                if (material == null) {
                    MessageUtil.consoleWarn("level.yml 中存在未知的方块限制类型: " + key);
                    continue;
                }
                long limit = limitsSection.getLong(key);
                limits.put(material, limit);
            }
        }
        this.blockLimits = limits;

        // 加载等级公式
        this.levelFormula = config.getString("level-formula", "{experience} / 100");

        // 加载等级经验值（幂函数增长）
        ConfigurationSection costSection = config.getConfigurationSection("level-cost");
        if (costSection != null) {
            this.levelExpBase = costSection.getDouble("base", 100.0);
            this.levelExpPower = costSection.getDouble("power", 0.0);
        } else {
            this.levelExpBase = 100.0;
            this.levelExpPower = 0.0;
        }

        // 加载超额经验值递减配置
        ConfigurationSection dimSection = config.getConfigurationSection("diminishing");
        if (dimSection != null) {
            this.diminishingEnabled = dimSection.getBoolean("enabled", false);
            this.diminishingDecay = dimSection.getDouble("decay", 0.001);
            this.diminishingMinimum = dimSection.getDouble("minimum", 1.0);
        } else {
            this.diminishingEnabled = false;
        }

        // 加载模板基线开关（默认启用，保持历史行为）
        ConfigurationSection baselineSection = config.getConfigurationSection("baseline");
        this.baselineEnabled = baselineSection == null || baselineSection.getBoolean("enabled", true);

        MessageUtil.consolePrint("已加载 " + experienceValues.size() + " 种方块经验值和 " + blockLimits.size() + " 种方块阈值");
    }

    /**
     * 获取指定方块的经验值。未在 blocks 中配置时默认返回 1.0。
     */
    public double getExperience(Material material) {
        if (material == null || material.isAir()) return 0;
        Double value = experienceValues.get(material);
        return value != null ? value : 1.0;
    }

    /**
     * 获取指定方块的最大计数阈值。未配置时返回 Long.MAX_VALUE（无限制）。
     */
    public long getLimit(Material material) {
        if (material == null) return Long.MAX_VALUE;
        Long limit = blockLimits.get(material);
        return limit != null ? limit : Long.MAX_VALUE;
    }

    /**
     * 获取等级计算公式。默认 {@code {experience} / 100}。
     */
    public String getLevelFormula() {
        return levelFormula;
    }

    /**
     * 获取等级经验值-基础值（升到 1 级所需的基础经验值）。
     */
    public double getLevelExpBase() {
        return levelExpBase;
    }

    /**
     * 获取等级经验值-幂函数指数（控制每级增长幅度）。
     */
    public double getLevelExpPower() {
        return levelExpPower;
    }

    /**
     * 是否配置了等级经验值（power > 0 时启用）。
     */
    public boolean hasLevelCost() {
        return levelExpPower > 0;
    }

    /**
     * 超额经验值递减是否启用。
     */
    public boolean isDiminishingEnabled() {
        return diminishingEnabled;
    }

    /**
     * 模板基线扣除是否启用。
     * <p>
     * 启用时：岛屿创建时扫描模板(schematic)保存基线方块计数，等级计算时扣除基线数量，
     * 使玩家仅凭"新放置的方块"获得经验。
     * 关闭时：创建岛屿时不保存基线，等级计算时也不扣除（模板方块全额计入经验）。
     * 已保存基线的旧岛屿在关闭后也会停止扣除，重新启用即恢复，不会丢失已保存的基线数据。
     */
    public boolean isBaselineEnabled() {
        return baselineEnabled;
    }

    /**
     * 获取超额经验值递减系数。
     */
    public double getDiminishingDecay() {
        return diminishingDecay;
    }

    /**
     * 获取超额方块的最低经验值。
     */
    public double getDiminishingMinimum() {
        return diminishingMinimum;
    }

    /**
     * 返回所有已配置的方块经验值映射（不可变）
     */
    public Map<Material, Double> getExperienceValues() {
        return Collections.unmodifiableMap(experienceValues);
    }

    /**
     * 返回所有已配置的方块阈值映射（不可变）
     */
    public Map<Material, Long> getBlockLimits() {
        return Collections.unmodifiableMap(blockLimits);
    }
}