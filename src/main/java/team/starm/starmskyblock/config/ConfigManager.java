package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * StarMSkyblock 插件的核心配置文件管理器
 * <p>
 * 负责加载 config.yml 中的所有配置项，并提供全局访问方法。
 * 该类已移除所有旧格式兼容代码，仅保留当前最新配置结构（schematics 下嵌套 teleport-offset）。
 * <p>
 * 主要功能：
 * 1. 读取岛屿基础参数（大小、间距、最大半径等）
 * 2. 加载多岛屿类型结构文件（schematics）及其独立的传送偏移量
 * 3. 提供生物群系、世界名称、生成高度等全局配置
 * 4. 提供便捷的 Getter 方法，供插件其他模块调用
 */
public class ConfigManager {

    private final StarMSkyblock plugin;
    // ==================== 基础岛屿配置 ====================
    private int islandRadius; // 岛屿初始半径（区块）
    private int islandSpacing; // 岛屿之间间隔距离（区块）
    private int islandMaxRadius; // 岛屿可扩展的最大半径（区块）
    private boolean teleportOnCreate; // 创建岛屿后是否自动传送玩家
    private boolean showBorderDefault; // 岛屿边界是否默认显示
    // ==================== 结构文件配置（支持多岛屿类型） ====================
    private Map<String, String> normalSchematics; // 岛屿类型 -> 结构文件名
    private Map<String, SchematicConfig> schematicConfigs; // 岛屿类型 -> 完整配置（含偏移）
    private String defaultNormalSchematicId; // 默认使用的岛屿类型ID
    // 下界和末地结构（当前版本自动根据主世界结构文件名生成 _nether / _the_end 后缀）
    // (netherSchematic / endSchematic 字段已移除，改用 getNetherSchematicFileName / getEndSchematicFileName)
    // ==================== 世界与生物群系配置 ====================
    private String biomeNormal; // 主世界生物群系
    private String biomeNether; // 下界生物群系
    private String biomeEnd; // 末地生物群系
    private String worldNameNormal; // 主世界名称
    private String worldNameNether; // 下界世界名称
    private String worldNameEnd; // 末地世界名称
    private int islandHeight; // 岛屿生成高度（以基岩方块为中心点的 Y 坐标）
    // ==================== 其他功能配置 ====================
    private int maxDeleteTimes; // 玩家最多可删除自己岛屿的次数
    private boolean allowSetspawnInNether; // 是否允许在下界使用 /is setspawn
    private boolean allowSetspawnInEnd; // 是否允许在末地使用 /is setspawn
    private boolean allowEndPortalInNether; // 是否允许在下界放置末地传送门
    private boolean allowEndPortalInEnd; // 是否允许在末地放置末地传送门
    private long permissionMessageCooldown; // 权限提示消息冷却时间（毫秒）
    private int maxNameLength; // 岛屿名称最大长度（颜色代码不计入）
    private int renameCooldown; // 岛屿重命名冷却时间（秒）
    private int teleportCountdown; // 岛屿传送倒计时（秒）
    private boolean useFawe; // 是否使用 FAWE 模式
    private String defaultIslandCommand; // /is 无参数时执行的子命令（默认 spawn）
    private boolean obsidianToLava; // 是否允许空桶右键黑曜石转化为熔岩桶

    public ConfigManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化配置管理器
     * 插件启用时调用
     */
    public void initialize() {
        loadConfig();
    }

    /**
     * 加载 config.yml 配置
     * <p>
     * 这是整个类的核心方法，会完整解析当前最新格式的配置文件。
     * 不再包含任何旧格式（平铺 teleport-offset-x 等）的兼容逻辑。
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // ====================== 基础岛屿参数 ======================
        this.islandRadius = config.getInt("island-radius", 5);
        this.islandSpacing = config.getInt("island-spacing", 5);
        this.islandMaxRadius = config.getInt("island-max-radius", 16);

        this.teleportOnCreate = config.getBoolean("teleport-on-create", true);
        this.showBorderDefault = config.getBoolean("show-border-default", true);

        // ====================== 结构文件配置（schematics 部分） ======================
        this.normalSchematics = new HashMap<>();
        this.schematicConfigs = new HashMap<>();

        ConfigurationSection schematicsSection = config.getConfigurationSection("schematics");
        if (schematicsSection != null) {
            for (String islandType : schematicsSection.getKeys(false)) {
                // 跳过特殊配置项（default-type）
                if (islandType.equals("default-type")) {
                    continue;
                }

                ConfigurationSection islandSection = schematicsSection.getConfigurationSection(islandType);
                if (islandSection != null) {
                    // 读取结构文件名
                    String fileName = islandSection.getString("file", "default.schem");
                    normalSchematics.put(islandType, fileName);

                    // 读取每个环境的传送偏移（新格式嵌套结构）
                    double normalX = islandSection.getDouble("teleport-offset.normal.x", 0.5);
                    double normalY = islandSection.getDouble("teleport-offset.normal.y", 5);
                    double normalZ = islandSection.getDouble("teleport-offset.normal.z", 2.2);

                    double netherX = islandSection.getDouble("teleport-offset.nether.x", 0);
                    double netherY = islandSection.getDouble("teleport-offset.nether.y", 5);
                    double netherZ = islandSection.getDouble("teleport-offset.nether.z", 0);

                    double endX = islandSection.getDouble("teleport-offset.end.x", 0);
                    double endY = islandSection.getDouble("teleport-offset.end.y", 5);
                    double endZ = islandSection.getDouble("teleport-offset.end.z", 0);

                    schematicConfigs.put(islandType, new SchematicConfig(fileName,
                            normalX, normalY, normalZ,
                            netherX, netherY, netherZ,
                            endX, endY, endZ));
                }
            }
        }

        // 如果配置文件中没有任何岛屿类型，则自动创建一个默认配置
        if (normalSchematics.isEmpty()) {
            normalSchematics.put("default", "default.schem");
            schematicConfigs.put("default", new SchematicConfig("default.schem",
                    0.5, 5, 2.2, 0, 5, 0, 0, 5, 0));
        }

        // 读取默认岛屿类型
        this.defaultNormalSchematicId = config.getString("schematics.default-type", "default");
        if (!normalSchematics.containsKey(this.defaultNormalSchematicId)) {
            this.defaultNormalSchematicId = normalSchematics.keySet().iterator().next();
            MessageUtil.consoleWarn("配置文件中 schematics.default-type 指定的类型不存在，将自动使用: " + this.defaultNormalSchematicId);
        }

        // ====================== 下界 & 末地结构文件（自动生成） ======================
        // 文件名由 getNetherSchematicFileName / getEndSchematicFileName 动态生成

        // ====================== 生物群系配置 ======================
        this.biomeNormal = config.getString("biome.normal", "PLAINS");
        this.biomeNether = config.getString("biome.nether", "NETHER_WASTES");
        this.biomeEnd = config.getString("biome.end", "THE_END");

        // ====================== 世界名称配置 ======================
        this.worldNameNormal = config.getString("world-name", "StarM_Skyblock");
        this.worldNameNether = this.worldNameNormal + "_nether";
        this.worldNameEnd = this.worldNameNormal + "_the_end";

        // ====================== 岛屿生成高度 ======================
        this.islandHeight = config.getInt("island-height", 100);

        // ====================== 其他功能配置 ======================
        this.maxDeleteTimes = config.getInt("max-delete-times", 3);

        this.allowSetspawnInNether = config.getBoolean("allow-setspawn-in-nether", true);
        this.allowSetspawnInEnd = config.getBoolean("allow-setspawn-in-end", true);

        this.allowEndPortalInNether = config.getBoolean("allow-end-portal-in-nether", false);
        this.allowEndPortalInEnd = config.getBoolean("allow-end-portal-in-end", false);

        this.permissionMessageCooldown = config.getLong("permission-message-cooldown", 1000);

        this.maxNameLength = config.getInt("max-name-length", 32);

        this.renameCooldown = config.getInt("rename-cooldown", 300);

        this.teleportCountdown = config.getInt("teleport-countdown", 3);

        // ====================== WorldEdit 引擎模式 ======================
        String worldEditMode = config.getString("worldedit-mode", "we");
        this.useFawe = "fawe".equalsIgnoreCase(worldEditMode);

        this.defaultIslandCommand = config.getString("default-island-command", "spawn");

        this.obsidianToLava = config.getBoolean("obsidian-to-lava", false);
    }

    // ==================== Getter 方法（按功能分类） ====================

    /**
     * @return 岛屿初始半径（区块）
     */
    public int getIslandRadius() {
        return islandRadius;
    }

    /**
     * @return 岛屿之间间隔距离（区块）
     */
    public int getIslandSpacing() {
        return islandSpacing;
    }

    /**
     * @return 岛屿可扩展的最大半径（区块）
     */
    public int getIslandMaxRadius() {
        return islandMaxRadius;
    }

    /**
     * @return 创建岛屿后是否自动传送玩家
     */
    public boolean isTeleportOnCreate() {
        return teleportOnCreate;
    }

    /**
     * @return 岛屿边界是否默认显示
     */
    public boolean isShowBorderDefault() {
        return showBorderDefault;
    }

    /**
     * @return 所有主世界岛屿类型 -> 结构文件名的映射
     */
    public Map<String, String> getNormalSchematics() {
        return normalSchematics;
    }

    /**
     * @return 默认使用的岛屿类型ID
     */
    public String getDefaultNormalSchematicId() {
        return defaultNormalSchematicId;
    }

    /**
     * 根据岛屿类型ID获取对应的主世界结构文件名
     *
     * @param id 岛屿类型ID（可为null）
     * @return 结构文件名，若ID不存在则返回默认类型的文件名
     */
    public String getNormalSchematicFileName(String id) {
        if (id == null || !normalSchematics.containsKey(id)) {
            return normalSchematics.get(defaultNormalSchematicId);
        }
        return normalSchematics.get(id);
    }

    /**
     * 根据主世界结构ID自动生成下界结构文件名（添加 _nether 后缀）
     */
    public String getNetherSchematicFileName(String schematicId) {
        String normalName = getNormalSchematicFileName(schematicId);
        if (normalName == null || normalName.isEmpty()) {
            return "default_nether.schem";
        }
        String baseName = normalName.replace(".schem", "");
        return baseName + "_nether.schem";
    }

    /**
     * 根据主世界结构ID自动生成末地结构文件名（添加 _the_end 后缀）
     */
    public String getEndSchematicFileName(String schematicId) {
        String normalName = getNormalSchematicFileName(schematicId);
        if (normalName == null || normalName.isEmpty()) {
            return "default_the_end.schem";
        }
        String baseName = normalName.replace(".schem", "");
        return baseName + "_the_end.schem";
    }

    public String getBiomeNormal() {
        return biomeNormal;
    }

    public String getBiomeNether() {
        return biomeNether;
    }

    public String getBiomeEnd() {
        return biomeEnd;
    }

    public String getWorldNameNormal() {
        return worldNameNormal;
    }

    public String getWorldNameNether() {
        return worldNameNether;
    }

    public String getWorldNameEnd() {
        return worldNameEnd;
    }

    public int getIslandHeight() {
        return islandHeight;
    }

    public int getMaxDeleteTimes() {
        return maxDeleteTimes;
    }

    public boolean isAllowSetspawnInNether() {
        return allowSetspawnInNether;
    }

    public boolean isAllowSetspawnInEnd() {
        return allowSetspawnInEnd;
    }

    public boolean isAllowEndPortalInNether() {
        return allowEndPortalInNether;
    }

    public boolean isAllowEndPortalInEnd() {
        return allowEndPortalInEnd;
    }

    public long getPermissionMessageCooldown() {
        return permissionMessageCooldown;
    }

    public int getMaxNameLength() {
        return maxNameLength;
    }

    public int getRenameCooldown() {
        return renameCooldown;
    }

    public int getTeleportCountdown() {
        return teleportCountdown;
    }

    public boolean isUseFawe() {
        return useFawe;
    }

    public String getDefaultIslandCommand() {
        return defaultIslandCommand;
    }

    public boolean isObsidianToLava() {
        return obsidianToLava;
    }

    /**
     * 根据岛屿类型和世界类型获取对应的传送偏移量
     *
     * @param schematicId 岛屿类型ID
     * @param worldType   世界类型（NORMAL / NETHER / END）
     * @return double[3] = {x, y, z}
     */
    public double[] getTeleportOffsetsBySchematicAndWorldType(String schematicId, Island.WorldType worldType) {
        SchematicConfig config = getSchematicConfig(schematicId);
        if (config == null) {
            return new double[]{0.5, 5, 2.2};
        }

        switch (worldType) {
            case NETHER:
                return new double[]{config.netherOffsetX(), config.netherOffsetY(), config.netherOffsetZ()};
            case END:
                return new double[]{config.endOffsetX(), config.endOffsetY(), config.endOffsetZ()};
            default:
                return new double[]{config.normalOffsetX(), config.normalOffsetY(), config.normalOffsetZ()};
        }
    }

    public SchematicConfig getSchematicConfig(String schematicId) {
        if (schematicId == null || !schematicConfigs.containsKey(schematicId)) {
            return schematicConfigs.get(defaultNormalSchematicId);
        }
        return schematicConfigs.get(schematicId);
    }

}
