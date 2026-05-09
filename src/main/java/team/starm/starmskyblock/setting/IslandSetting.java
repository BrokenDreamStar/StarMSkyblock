package team.starm.starmskyblock.setting;

public enum IslandSetting {

    PVP("PVP"),
    ANIMAL_SPAWN("自然生成动物"),
    MONSTER_SPAWN("自然生成怪物"),
    SPAWNER_SPAWN("刷怪笼生成生物"),
    FIRE_SPREAD("火势蔓延"),
    ENDERMAN_GRIEF("末影人搬运方块"),
    GHAST_FIREBALL_GRIEF("恶魂火球破坏方块"),
    CREEPER_EXPLOSION("苦力怕爆炸"),
    TNT_EXPLOSION("TNT爆炸"),
    WITHER_GRIEF("凋灵破坏方块");

    private final String displayName;

    IslandSetting(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConfigKey() {
        return name().toLowerCase();
    }

    /**
     * 从配置键（小写枚举名）获取枚举常量
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
