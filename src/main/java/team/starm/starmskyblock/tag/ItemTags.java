package team.starm.starmskyblock.tag;

import org.bukkit.Material;

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
}
