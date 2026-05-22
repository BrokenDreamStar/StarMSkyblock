package team.starm.starmskyblock.tag;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumSet;
import java.util.Set;

/**
 * 物品标签工具类
 *
 * 预定义了一系列 Material 集合，用于方便地判断物品/方块是否属于某种分类。
 * 这些标签涵盖马铠、桶、矿车、染料、铜系列方块、刷怪蛋等常见分类，
 * 避免在业务代码中反复编写 instanceof / equals / startsWith 等判断。
 *
 * 部分集合（如 WAXABLE_BLOCKS、COPPER_VARIANTS、SPAWN_EGGS）在静态初始化块中
 * 通过遍历所有 Material 自动构建，以适配不同 Minecraft 版本的材料列表
 */
public class ItemTags {
    /**
     * 所有种类的马铠（从皮革到下界合金）
     * 用于判断物品是否为可装备的马铠
     */
    public static final Set<Material> HORSE_ARMORS = EnumSet.of(
            Material.LEATHER_HORSE_ARMOR,
            Material.COPPER_HORSE_ARMOR,
            Material.IRON_HORSE_ARMOR,
            Material.GOLDEN_HORSE_ARMOR,
            Material.DIAMOND_HORSE_ARMOR,
            Material.NETHERITE_HORSE_ARMOR
    );

    /**
     * 所有种类的鹦鹉螺铠（1.21 新增的鹦鹉螺可装备盔甲）
     * 用于判断物品是否为可装备的鹦鹉螺铠
     */
    public static final Set<Material> NAUTILUS_ARMORS = EnumSet.of(
            Material.COPPER_NAUTILUS_ARMOR,
            Material.IRON_NAUTILUS_ARMOR,
            Material.GOLDEN_NAUTILUS_ARMOR,
            Material.DIAMOND_NAUTILUS_ARMOR,
            Material.NETHERITE_NAUTILUS_ARMOR
    );

    /**
     * 所有种类的桶(除奶桶)
     */
    public static final Set<Material> BUCKETS = EnumSet.of(
            Material.BUCKET,
            Material.WATER_BUCKET,
            Material.LAVA_BUCKET,
            Material.POWDER_SNOW_BUCKET,
            Material.AXOLOTL_BUCKET,
            Material.COD_BUCKET,
            Material.PUFFERFISH_BUCKET,
            Material.SALMON_BUCKET,
            Material.TROPICAL_FISH_BUCKET,
            Material.TADPOLE_BUCKET
    );

    /**
     * 所有种类的矿车
     */
    public static final Set<Material> MINECARTS = EnumSet.of(
            Material.MINECART,
            Material.CHEST_MINECART,
            Material.FURNACE_MINECART,
            Material.HOPPER_MINECART,
            Material.TNT_MINECART
    );

    /**
     * 所有种类的染料
     */
    public static final Set<Material> DYES = EnumSet.of(
            Material.WHITE_DYE,
            Material.ORANGE_DYE,
            Material.MAGENTA_DYE,
            Material.LIGHT_BLUE_DYE,
            Material.YELLOW_DYE,
            Material.LIME_DYE,
            Material.PINK_DYE,
            Material.GRAY_DYE,
            Material.LIGHT_GRAY_DYE,
            Material.CYAN_DYE,
            Material.PURPLE_DYE,
            Material.BLUE_DYE,
            Material.BROWN_DYE,
            Material.GREEN_DYE,
            Material.RED_DYE,
            Material.BLACK_DYE
    );
    
    /**
     * 所有可涂蜡的方块集合
     *
     * 静态初始化时遍历所有 Material，筛选出：
     * 1. 名称包含 "COPPER" 且不是矿石/原矿的方块（铜系列方块）
     * 2. 避雷针（LIGHTNING_ROD）
     * 排除已涂蜡（WAXED_）的变种
     *
     * 用于自动补全涂蜡/脱蜡操作的可选方块列表
     */
    public static final Set<Material> WAXABLE_BLOCKS = EnumSet.noneOf(Material.class);

    static {
        for (Material material : Material.values()) {
            if (material.isBlock()) {
                String name = material.name();

                // 跳过已涂蜡的变种，只收集未涂蜡的原始/锈蚀/氧化状态
                if (name.startsWith("WAXED_")) {
                    continue;
                }

                // 铜系列方块（包括铜块、切制铜块、铜格栅、铜灯等）加上避雷针
                if (name.contains("COPPER") && !name.contains("ORE") && !name.contains("RAW")) {
                    WAXABLE_BLOCKS.add(material);
                } else if (name.equals("LIGHTNING_ROD")) {
                    WAXABLE_BLOCKS.add(material);
                }
            }
        }
    }

    /**
     * 所有可以被脱蜡/除锈处理的铜方块变种集合
     *
     * 包括所有已涂蜡、已锈蚀、已氧化的铜系列方块，
     * 以及铜傀儡雕像（来自 Minecraft 1.21 的铜傀儡投票内容）。
     *
     * 用于脱蜡（刮除蜡层）和除锈（用斧头刮锈）操作的可选方块列表
     */
    public static final Set<Material> COPPER_VARIANTS = EnumSet.noneOf(Material.class);

    static {
        for (Material type : Material.values()) {
            String name = type.name();

            // 判断是否为铜系列方块或避雷针
            boolean isCopperOrRod = name.contains("COPPER") || name.contains("LIGHTNING_ROD");
            // 判断是否为已涂蜡/已锈蚀的变种
            boolean isRustedOrWaxed = name.contains("WAXED_") ||
                    name.contains("EXPOSED_") ||
                    name.contains("WEATHERED_") ||
                    name.contains("OXIDIZED_");
            // 铜傀儡雕像通过内置 Tag 判断
            boolean isCopperGolem = Tag.COPPER_GOLEM_STATUES.isTagged(type);

            if ((isCopperOrRod && isRustedOrWaxed) || isCopperGolem) {
                COPPER_VARIANTS.add(type);
            }
        }
    }

    /**
     * 所有种类的刷怪蛋集合
     *
     * 动态遍历所有 Material，筛选出名称以 _SPAWN_EGG 结尾的物品，
     * 无需手动维护列表，自动适配新版本新增的刷怪蛋
     */
    public static final Set<Material> SPAWN_EGGS = EnumSet.noneOf(Material.class);

    static {
        for (Material material : Material.values()) {
            if (material.name().endsWith("_SPAWN_EGG")) {
                SPAWN_EGGS.add(material);
            }
        }
    }
}
