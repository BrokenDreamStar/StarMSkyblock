package team.starm.starmskyblock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private volatile int islandRadius; // 岛屿初始半径（区块）
    private volatile int islandSpacing; // 岛屿之间间隔距离（区块）
    private volatile int islandMaxRadius; // 岛屿可扩展的最大半径（区块）
    private volatile boolean teleportOnCreate; // 创建岛屿后是否自动传送玩家
    private volatile boolean showBorderDefault; // 岛屿边界是否默认显示
    // ==================== 结构文件配置（支持多岛屿类型） ====================
    private volatile Map<String, String> normalSchematics; // 岛屿类型 -> 结构文件名
    private volatile Map<String, SchematicConfig> schematicConfigs; // 岛屿类型 -> 完整配置（含偏移）
    private volatile String defaultNormalSchematicId; // 默认使用的岛屿类型ID
    // 下界和末地结构（当前版本自动根据主世界结构文件名生成 _nether / _the_end 后缀）
    // (netherSchematic / endSchematic 字段已移除，改用 getNetherSchematicFileName / getEndSchematicFileName)
    // ==================== 世界与生物群系配置 ====================
    private volatile String biomeNormal; // 主世界生物群系
    private volatile String biomeNether; // 下界生物群系
    private volatile String biomeEnd; // 末地生物群系
    private volatile String worldNameNormal; // 主世界名称
    private volatile String worldNameNether; // 下界世界名称
    private volatile String worldNameEnd; // 末地世界名称
    private volatile int islandHeight; // 岛屿生成高度（以基岩方块为中心点的 Y 坐标）
    // ==================== 其他功能配置 ====================
    private volatile int maxDeleteTimes; // 玩家最多可删除自己岛屿的次数
    private volatile boolean allowSetspawnInNether; // 是否允许在下界使用 /is setspawn
    private volatile boolean allowSetspawnInEnd; // 是否允许在末地使用 /is setspawn
    private volatile boolean allowEndPortalInNether; // 是否允许在下界放置末地传送门
    private volatile boolean allowEndPortalInEnd; // 是否允许在末地放置末地传送门
    private volatile long permissionMessageCooldown; // 权限提示消息冷却时间（毫秒）
    private volatile int maxNameLength; // 岛屿名称最大长度（颜色代码不计入）
    private volatile int renameCooldown; // 岛屿重命名冷却时间（秒）
    private volatile int levelCooldown; // 岛屿等级计算冷却时间（秒）
    private volatile int teleportCountdown; // 岛屿传送倒计时（秒）
    private volatile boolean useFawe; // 是否使用 FAWE 模式
    private volatile String defaultIslandCommand; // /is 无参数时执行的子命令（默认 spawn）
    private volatile boolean obsidianToLava; // 是否允许空桶右键黑曜石转化为熔岩桶
    private volatile boolean setRespawnOnJoin; // 创建或加入岛屿时是否自动设置重生点
    private volatile String fallbackSpawnWorld; // 无床/重生锚时的重生点世界名称（空字符串表示禁用）
    private volatile double fallbackSpawnX; // 无床/重生锚时的重生点 X 坐标
    private volatile double fallbackSpawnY; // 无床/重生锚时的重生点 Y 坐标
    private volatile double fallbackSpawnZ; // 无床/重生锚时的重生点 Z 坐标
    private volatile float fallbackSpawnYaw; // 无床/重生锚时的重生点偏航角（水平朝向）
    private volatile float fallbackSpawnPitch; // 无床/重生锚时的重生点俯仰角（垂直朝向）
    private volatile Set<String> publicWorlds = Collections.emptySet(); // 公共世界列表
    private volatile String locale = "zh_CN"; // 当前激活的 i18n locale
    // ==================== 告示牌配置 ====================
    private volatile boolean signEnabled; // 是否启用岛屿生成时的告示牌文字写入
    private volatile List<String> signLines = Collections.emptyList(); // 告示牌正面文字行模板

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
     * 重载 config.yml —— 与其他 *ConfigManager.reload() 接口对齐。
     * 行为等同于 {@link #loadConfig()}，保留同名方法以便 {@code ReloadCommand}
     * 与其他配置管理器以一致方式调用。
     */
    public void reload() {
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
        int rawRadius = config.getInt(ConfigKeys.ISLAND_RADIUS, 5);
        if (rawRadius < 1) {
            MessageUtil.consoleWarn("island-radius 必须 ≥ 1，已自动修正为 1");
            rawRadius = 1;
        }
        this.islandRadius = rawRadius;

        int rawSpacing = config.getInt(ConfigKeys.ISLAND_SPACING, 5);
        if (rawSpacing < 0) {
            MessageUtil.consoleWarn("island-spacing 不能为负数，已自动修正为 0");
            rawSpacing = 0;
        }
        this.islandSpacing = rawSpacing;

        int rawMaxRadius = config.getInt(ConfigKeys.ISLAND_MAX_RADIUS, 16);
        if (rawMaxRadius < this.islandRadius) {
            MessageUtil.consoleWarn("island-max-radius 不能小于 island-radius，已自动修正为 " + this.islandRadius);
            rawMaxRadius = this.islandRadius;
        }
        this.islandMaxRadius = rawMaxRadius;

        this.teleportOnCreate = config.getBoolean(ConfigKeys.TELEPORT_ON_CREATE, true);
        this.showBorderDefault = config.getBoolean(ConfigKeys.SHOW_BORDER_DEFAULT, true);

        // ====================== 结构文件配置（schematics 部分） ======================
        this.normalSchematics = new HashMap<>();
        this.schematicConfigs = new HashMap<>();

        ConfigurationSection schematicsSection = config.getConfigurationSection(ConfigKeys.SCHEMATICS);
        if (schematicsSection != null) {
            for (String islandType : schematicsSection.getKeys(false)) {
                // 跳过特殊配置项（default-type）
                if (islandType.equals("default-type")) {
                    continue;
                }

                ConfigurationSection islandSection = schematicsSection.getConfigurationSection(islandType);
                if (islandSection != null) {
                    // 读取结构文件名
                    String fileName = islandSection.getString(ConfigKeys.SCHEMATIC_FILE, "default.schem");
                    normalSchematics.put(islandType, fileName);

                    // 读取每个环境的传送偏移及朝向（新格式嵌套结构）
                    double normalX = islandSection.getDouble("teleport-offset.normal.x", 0.5);
                    double normalY = islandSection.getDouble("teleport-offset.normal.y", 5);
                    double normalZ = islandSection.getDouble("teleport-offset.normal.z", 2.2);
                    float normalYaw = (float) islandSection.getDouble("teleport-offset.normal.yaw", 0.0);
                    float normalPitch = (float) islandSection.getDouble("teleport-offset.normal.pitch", 0.0);

                    double netherX = islandSection.getDouble("teleport-offset.nether.x", 0);
                    double netherY = islandSection.getDouble("teleport-offset.nether.y", 5);
                    double netherZ = islandSection.getDouble("teleport-offset.nether.z", 0);
                    float netherYaw = (float) islandSection.getDouble("teleport-offset.nether.yaw", 0.0);
                    float netherPitch = (float) islandSection.getDouble("teleport-offset.nether.pitch", 0.0);

                    double endX = islandSection.getDouble("teleport-offset.end.x", 0);
                    double endY = islandSection.getDouble("teleport-offset.end.y", 5);
                    double endZ = islandSection.getDouble("teleport-offset.end.z", 0);
                    float endYaw = (float) islandSection.getDouble("teleport-offset.end.yaw", 0.0);
                    float endPitch = (float) islandSection.getDouble("teleport-offset.end.pitch", 0.0);

                    schematicConfigs.put(islandType, new SchematicConfig(fileName,
                            normalX, normalY, normalZ, normalYaw, normalPitch,
                            netherX, netherY, netherZ, netherYaw, netherPitch,
                            endX, endY, endZ, endYaw, endPitch));
                }
            }
        }

        // 如果配置文件中没有任何岛屿类型，则自动创建一个默认配置
        if (normalSchematics.isEmpty()) {
            normalSchematics.put("default", "default.schem");
            schematicConfigs.put("default", new SchematicConfig("default.schem",
                    0.5, 5, 2.2, 0f, 0f,
                    0, 5, 0, 0f, 0f,
                    0, 5, 0, 0f, 0f));
        }

        // 读取默认岛屿类型
        this.defaultNormalSchematicId = config.getString(ConfigKeys.SCHEMATICS_DEFAULT_TYPE, "default");
        if (!normalSchematics.containsKey(this.defaultNormalSchematicId)) {
            this.defaultNormalSchematicId = normalSchematics.keySet().iterator().next();
            MessageUtil.consoleWarn("配置文件中 schematics.default-type 指定的类型不存在，将自动使用: " + this.defaultNormalSchematicId);
        }

        // ====================== 下界 & 末地结构文件（自动生成） ======================
        // 文件名由 getNetherSchematicFileName / getEndSchematicFileName 动态生成

        // ====================== 生物群系配置 ======================
        this.biomeNormal = config.getString(ConfigKeys.BIOME_NORMAL, "PLAINS");
        this.biomeNether = config.getString(ConfigKeys.BIOME_NETHER, "NETHER_WASTES");
        this.biomeEnd = config.getString(ConfigKeys.BIOME_END, "THE_END");

        // ====================== 世界名称配置 ======================
        this.worldNameNormal = config.getString(ConfigKeys.WORLD_NAME, "StarM_Skyblock");
        this.worldNameNether = this.worldNameNormal + "_nether";
        this.worldNameEnd = this.worldNameNormal + "_the_end";

        // ====================== 岛屿生成高度 ======================
        this.islandHeight = config.getInt(ConfigKeys.ISLAND_HEIGHT, 100);

        // ====================== 其他功能配置 ======================
        this.maxDeleteTimes = config.getInt(ConfigKeys.MAX_DELETE_TIMES, 3);

        this.allowSetspawnInNether = config.getBoolean(ConfigKeys.ALLOW_SETSPAWN_IN_NETHER, true);
        this.allowSetspawnInEnd = config.getBoolean(ConfigKeys.ALLOW_SETSPAWN_IN_END, true);

        this.allowEndPortalInNether = config.getBoolean(ConfigKeys.ALLOW_END_PORTAL_IN_NETHER, false);
        this.allowEndPortalInEnd = config.getBoolean(ConfigKeys.ALLOW_END_PORTAL_IN_END, false);

        this.permissionMessageCooldown = config.getLong(ConfigKeys.PERMISSION_MESSAGE_COOLDOWN, 1000);

        this.maxNameLength = config.getInt(ConfigKeys.MAX_NAME_LENGTH, 32);

        this.renameCooldown = config.getInt(ConfigKeys.RENAME_COOLDOWN, 300);

        this.levelCooldown = config.getInt(ConfigKeys.LEVEL_COOLDOWN, 60);

        this.teleportCountdown = config.getInt(ConfigKeys.TELEPORT_COUNTDOWN, 3);

        // ====================== WorldEdit 引擎模式 ======================
        String worldEditMode = config.getString(ConfigKeys.WORLDEDIT_MODE, "we");
        this.useFawe = "fawe".equalsIgnoreCase(worldEditMode);

        this.defaultIslandCommand = config.getString(ConfigKeys.DEFAULT_ISLAND_COMMAND, "spawn");

        this.obsidianToLava = config.getBoolean(ConfigKeys.OBSIDIAN_TO_LAVA, false);
        this.setRespawnOnJoin = config.getBoolean(ConfigKeys.SET_RESPAWN_ON_JOIN, true);

        // ====================== 回退重生点配置 ======================
        ConfigurationSection fallbackSection = config.getConfigurationSection(ConfigKeys.FALLBACK_SPAWN);
        if (fallbackSection != null) {
            this.fallbackSpawnWorld = fallbackSection.getString("world", "");
            this.fallbackSpawnX = fallbackSection.getDouble("x", 0.5);
            this.fallbackSpawnY = fallbackSection.getDouble("y", 64);
            this.fallbackSpawnZ = fallbackSection.getDouble("z", 0.5);
            this.fallbackSpawnYaw = (float) fallbackSection.getDouble("yaw", 0.0);
            this.fallbackSpawnPitch = (float) fallbackSection.getDouble("pitch", 0.0);
        } else {
            this.fallbackSpawnWorld = "";
            this.fallbackSpawnX = 0.5;
            this.fallbackSpawnY = 64;
            this.fallbackSpawnZ = 0.5;
            this.fallbackSpawnYaw = 0.0f;
            this.fallbackSpawnPitch = 0.0f;
        }

        this.publicWorlds = new HashSet<>(config.getStringList(ConfigKeys.PUBLIC_WORLDS));

        // ====================== i18n locale ======================
        // 仅原样存储，校验由 LanguageManager.validateLocale() 统一处理（避免正则重复）。
        this.locale = config.getString(ConfigKeys.LOCALE, "zh_CN");

        // ====================== 岛屿生成告示牌文字 ======================
        this.signEnabled = config.getBoolean(ConfigKeys.SIGN_ENABLED, true);
        this.signLines = new ArrayList<>(config.getStringList(ConfigKeys.SIGN_LINES));
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

    /**
     * @return 岛屿等级计算冷却时间（秒），0 表示无冷却
     */
    public int getLevelCooldown() {
        return levelCooldown;
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
     * @return 创建或加入岛屿时是否自动将重生点设为岛屿传送点
     */
    public boolean isSetRespawnOnJoin() {
        return setRespawnOnJoin;
    }

    /**
     * @return 无床/重生锚时的回退重生点世界名称，空字符串表示禁用
     */
    public String getFallbackSpawnWorld() {
        return fallbackSpawnWorld;
    }

    /**
     * @return 无床/重生锚时的回退重生点世界名称是否已配置且非空
     */
    public boolean hasFallbackSpawn() {
        return fallbackSpawnWorld != null && !fallbackSpawnWorld.isEmpty();
    }

    /**
     * @return 无床/重生锚时的回退重生点 X 坐标
     */
    public double getFallbackSpawnX() {
        return fallbackSpawnX;
    }

    /**
     * @return 无床/重生锚时的回退重生点 Y 坐标
     */
    public double getFallbackSpawnY() {
        return fallbackSpawnY;
    }

    /**
     * @return 无床/重生锚时的回退重生点 Z 坐标
     */
    public double getFallbackSpawnZ() {
        return fallbackSpawnZ;
    }

    /**
     * @return 无床/重生锚时的回退重生点偏航角（水平朝向）
     */
    public float getFallbackSpawnYaw() {
        return fallbackSpawnYaw;
    }

    /**
     * @return 无床/重生锚时的回退重生点俯仰角（垂直朝向）
     */
    public float getFallbackSpawnPitch() {
        return fallbackSpawnPitch;
    }

    /**
     * @param worldName 世界名称
     * @return 该世界是否被配置为公共世界
     */
    public boolean isPublicWorld(String worldName) {
        return publicWorlds.contains(worldName);
    }

    /**
     * @return 当前激活的 i18n locale（如 zh_CN、en_US）
     */
    public String getLocale() {
        return locale;
    }

    /**
     * 根据岛屿类型和世界类型获取对应的传送偏移量及朝向
     *
     * @param schematicId 岛屿类型ID
     * @param worldType   世界类型（NORMAL / NETHER / END）
     * @return double[5] = {x, y, z, yaw, pitch}
     */
    public double[] getTeleportOffsetsBySchematicAndWorldType(String schematicId, Island.WorldType worldType) {
        SchematicConfig config = getSchematicConfig(schematicId);
        if (config == null) {
            return new double[]{0.5, 5, 2.2, 0, 0};
        }

        switch (worldType) {
            case NETHER:
                return new double[]{config.netherOffsetX(), config.netherOffsetY(), config.netherOffsetZ(),
                        config.netherYaw(), config.netherPitch()};
            case END:
                return new double[]{config.endOffsetX(), config.endOffsetY(), config.endOffsetZ(),
                        config.endYaw(), config.endPitch()};
            default:
                return new double[]{config.normalOffsetX(), config.normalOffsetY(), config.normalOffsetZ(),
                        config.normalYaw(), config.normalPitch()};
        }
    }

    public SchematicConfig getSchematicConfig(String schematicId) {
        if (schematicId == null || !schematicConfigs.containsKey(schematicId)) {
            return schematicConfigs.get(defaultNormalSchematicId);
        }
        return schematicConfigs.get(schematicId);
    }

    /**
     * @return 是否启用岛屿生成时的告示牌文字写入
     */
    public boolean isSignEnabled() {
        return signEnabled;
    }

    /**
     * @return 告示牌正面文字行模板（副本），支持 &amp; 颜色代码与 PlaceholderAPI 变量
     */
    public List<String> getSignLines() {
        return new ArrayList<>(signLines);
    }

}
