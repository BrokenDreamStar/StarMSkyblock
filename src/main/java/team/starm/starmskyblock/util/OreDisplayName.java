package team.starm.starmskyblock.util;

import team.starm.starmskyblock.message.MessageUtil;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 刷石机矿石材质注册表。
 * <p>
 * 枚举常量本身只标识"哪些材质存在本地化显示名"（即 {@code messages/zh_CN.yml} 中
 * {@code material.<name>} 键的集合），中文显示名不再硬编码于此。
 * {@link #toChinese(String)} 对已知材质走 i18n 解析，未知材质原样返回材质枚举名。
 * </p>
 */
public enum OreDisplayName {

    COBBLESTONE,
    STONE,
    COAL_ORE,
    COPPER_ORE,
    LAPIS_ORE,
    IRON_ORE,
    GOLD_ORE,
    REDSTONE_ORE,
    DIAMOND_ORE,
    EMERALD_ORE,
    BASALT,
    NETHERRACK,
    NETHER_QUARTZ_ORE,
    NETHER_GOLD_ORE,
    GILDED_BLACKSTONE,
    ANCIENT_DEBRIS,
    GLOWSTONE,
    SOUL_SOIL,
    END_STONE,
    SAND;

    /** 已知（可翻译）的材质枚举名集合，用于区分"未知材质原样返回"与"走 i18n 解析" */
    private static final Set<String> KNOWN = new HashSet<>();

    static {
        for (OreDisplayName ore : values()) {
            KNOWN.add(ore.name());
        }
    }

    /**
     * 根据 Material 枚举名获取本地化显示名，未注册的材质返回原名。
     *
     * @param materialName Material 枚举名（如 {@code COBBLESTONE}）
     * @return 本地化显示名（如 {@code 圆石}），未注册时返回 {@code materialName} 原值
     */
    public static String toChinese(String materialName) {
        if (materialName == null) return null;
        if (!KNOWN.contains(materialName)) {
            return materialName;
        }
        return MessageUtil.format("material." + materialName.toLowerCase(Locale.ROOT));
    }
}
