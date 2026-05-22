package team.starm.starmskyblock.setting;

/**
 * 岛屿设置选项枚举
 * 定义了每个岛屿可独立开关的所有功能选项，
 * 每个枚举常量对应一个配置键和中文显示名称，
 * 用于控制岛屿上的PVP、生物生成、爆炸、破坏等行为
 */
public enum IslandSetting {

    /** 玩家间战斗（攻击伤害） */
    PVP("PVP"),
    /** 其他玩家能否传送到该岛屿 */
    TP("传送到该岛屿"),
    /** 是否自然生成友好生物（如牛、羊、鸡等） */
    ANIMAL_SPAWN("自然生成动物"),
    /** 是否自然生成敌对生物（如僵尸、骷髅等） */
    MONSTER_SPAWN("自然生成怪物"),
    /** 刷怪笼是否生成生物 */
    SPAWNER_SPAWN("刷怪笼生成生物"),
    /** 火能否在岛屿方块上蔓延 */
    FIRE_SPREAD("火势蔓延"),
    /** 末影人能否搬走岛屿上的方块 */
    ENDERMAN_GRIEF("末影人搬运方块"),
    /** 恶魂火球爆炸能否破坏方块 */
    GHAST_FIREBALL_GRIEF("恶魂火球破坏方块"),
    /** 苦力怕爆炸能否破坏方块 */
    CREEPER_EXPLOSION("苦力怕爆炸"),
    /** TNT爆炸能否破坏方块 */
    TNT_EXPLOSION("TNT爆炸"),
    /** 凋灵破坏方块行为是否允许 */
    WITHER_GRIEF("凋灵破坏方块"),
    /** 是否允许生成幻翼 */
    PHANTOM_SPAWN("生成幻翼");

    /** 该选项在菜单/消息中显示的中文名称 */
    private final String displayName;

    IslandSetting(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取该设置选项的中文显示名称，用于在菜单/消息中展示给玩家
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取该设置选项在配置文件（如 config.yml）中使用的键名，
     * 采用枚举常量的小写形式作为键
     */
    public String getConfigKey() {
        return name().toLowerCase();
    }

    /**
     * 从配置键（小写枚举名）反向查找对应的枚举常量，
     * 用于反序列化配置文件中的字符串到枚举类型
     *
     * @param key 配置键字符串（大小写不敏感）
     * @return 匹配的枚举常量，未找到时返回 null
     */
    public static IslandSetting fromKey(String key) {
        for (IslandSetting setting : values()) {
            if (setting.name().equalsIgnoreCase(key)) {
                return setting;
            }
        }
        return null;
    }
}
