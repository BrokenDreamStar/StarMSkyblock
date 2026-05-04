package team.starm.starmskyblock.tag;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumSet;
import java.util.Set;

public class ItemTags {
    /**
     * 所有种类的马铠
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
     * 所有种类的鹦鹉螺铠
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
     * 所有可涂蜡的方块
     */
    public static final Set<Material> WAXABLE_BLOCKS = EnumSet.noneOf(Material.class);

    static {
        for (Material material : Material.values()) {
            if (material.isBlock()) {
                String name = material.name();

                if (name.startsWith("WAXED_")) {
                    continue;
                }

                if (name.contains("COPPER") && !name.contains("ORE") && !name.contains("RAW")) {
                    WAXABLE_BLOCKS.add(material);
                } else if (name.equals("LIGHTNING_ROD")) {
                    WAXABLE_BLOCKS.add(material);
                }
            }
        }
    }

    /**
     * 所有铜制品变种（含涂蜡/氧化铜方块 和 铜傀儡雕像）
     */
    public static final Set<Material> COPPER_VARIANTS = EnumSet.noneOf(Material.class);

    static {
        for (Material type : Material.values()) {
            String name = type.name();

            boolean isCopperOrRod = name.contains("COPPER") || name.contains("LIGHTNING_ROD");
            boolean isRustedOrWaxed = name.contains("WAXED_") ||
                    name.contains("EXPOSED_") ||
                    name.contains("WEATHERED_") ||
                    name.contains("OXIDIZED_");
            boolean isCopperGolem = Tag.COPPER_GOLEM_STATUES.isTagged(type);

            if ((isCopperOrRod && isRustedOrWaxed) || isCopperGolem) {
                COPPER_VARIANTS.add(type);
            }
        }
    }
}
