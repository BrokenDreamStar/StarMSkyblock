package team.starm.starmskyblock.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 刷石机矿石中英文名称映射枚举。
 * 每个常量包含 Material 枚举名和对应的中文显示名。
 */
public enum OreDisplayName {

    COBBLESTONE("圆石"),
    STONE("石头"),
    COAL_ORE("煤矿石"),
    COPPER_ORE("铜矿石"),
    LAPIS_ORE("青金石矿石"),
    IRON_ORE("铁矿石"),
    GOLD_ORE("金矿石"),
    REDSTONE_ORE("红石矿石"),
    DIAMOND_ORE("钻石矿石"),
    EMERALD_ORE("绿宝石矿石"),
    BASALT("玄武岩"),
    NETHERRACK("下界岩"),
    NETHER_QUARTZ_ORE("下界石英矿"),
    NETHER_GOLD_ORE("下界金矿"),
    ANCIENT_DEBRIS("远古残骸"),
    GLOWSTONE("荧石"),
    SOUL_SOIL("灵魂土"),
    MAGMA_BLOCK("岩浆块"),
    END_STONE("末地石");

    private final String chineseName;

    private static final Map<String, String> MATERIAL_TO_CHINESE = new HashMap<>();
    private static final Map<String, String> CHINESE_TO_MATERIAL = new HashMap<>();

    static {
        for (OreDisplayName ore : values()) {
            MATERIAL_TO_CHINESE.put(ore.name(), ore.chineseName);
            CHINESE_TO_MATERIAL.put(ore.chineseName, ore.name());
        }
    }

    OreDisplayName(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    /**
     * 根据 Material 枚举名获取中文显示名，未找到则返回原名。
     */
    public static String toChinese(String materialName) {
        return MATERIAL_TO_CHINESE.getOrDefault(materialName, materialName);
    }

    /**
     * 根据中文名反查 Material 枚举名，未找到则返回 null。
     */
    public static String toMaterial(String chineseName) {
        return CHINESE_TO_MATERIAL.get(chineseName);
    }
}
