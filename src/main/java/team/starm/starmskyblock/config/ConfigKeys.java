package team.starm.starmskyblock.config;

/**
 * config.yml 顶层 key 常量。
 * <p>
 * 此前各 getXxx 调用散落字符串字面量（"island-radius" 等），拼写错误会静默回退到默认值
 * 且无编译期检查。集中到此处后 IDE 可定位所有引用点，重命名/拼写错误一目了然。
 * <p>
 * 嵌套子键（如 schematics.&lt;type&gt;.teleport-offset.normal.x）由
 * {@link ConfigManager#loadConfig()} 内部局部变量拼接，不入此常量类。
 */
public final class ConfigKeys {

    private ConfigKeys() {}

    // 基础岛屿参数
    public static final String ISLAND_RADIUS = "island-radius";
    public static final String ISLAND_SPACING = "island-spacing";
    public static final String ISLAND_MAX_RADIUS = "island-max-radius";
    public static final String TELEPORT_ON_CREATE = "teleport-on-create";
    public static final String SHOW_BORDER_DEFAULT = "show-border-default";

    // 结构文件
    public static final String SCHEMATICS = "schematics";
    public static final String SCHEMATICS_DEFAULT_TYPE = "schematics.default-type";
    public static final String SCHEMATIC_FILE = "file";

    // 生物群系
    public static final String BIOME_NORMAL = "biome.normal";
    public static final String BIOME_NETHER = "biome.nether";
    public static final String BIOME_END = "biome.end";

    // 世界名称与高度
    public static final String WORLD_NAME = "world-name";
    public static final String ISLAND_HEIGHT = "island-height";

    // 删除与传送门
    public static final String MAX_DELETE_TIMES = "max-delete-times";
    public static final String ALLOW_SETSPAWN_IN_NETHER = "allow-setspawn-in-nether";
    public static final String ALLOW_SETSPAWN_IN_END = "allow-setspawn-in-end";
    public static final String ALLOW_END_PORTAL_IN_NETHER = "allow-end-portal-in-nether";
    public static final String ALLOW_END_PORTAL_IN_END = "allow-end-portal-in-end";

    // 冷却与计数
    public static final String PERMISSION_MESSAGE_COOLDOWN = "permission-message-cooldown";
    public static final String MAX_NAME_LENGTH = "max-name-length";
    public static final String RENAME_COOLDOWN = "rename-cooldown";
    public static final String LEVEL_COOLDOWN = "level-cooldown";
    public static final String TELEPORT_COUNTDOWN = "teleport-countdown";

    // WorldEdit 引擎与默认命令
    public static final String WORLDEDIT_MODE = "worldedit-mode";
    public static final String DEFAULT_ISLAND_COMMAND = "default-island-command";

    // 杂项
    public static final String OBSIDIAN_TO_LAVA = "obsidian-to-lava";
    public static final String SET_RESPAWN_ON_JOIN = "set-respawn-on-join";

    // 回退重生点
    public static final String FALLBACK_SPAWN = "fallback-spawn";

    // i18n
    public static final String LOCALE = "locale";

    // 公共世界
    public static final String PUBLIC_WORLDS = "public-worlds";

    // 岛屿生成告示牌文字
    public static final String SIGN_ENABLED = "sign.enabled";
    public static final String SIGN_LINES = "sign.lines";
}
