package team.starm.starmskyblock.util;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Set;

public class TagsUtil {
    /**
     * 所有种类的马铠
     */
    public static final Set<Material> ITEMS_HORSE_ARMORS = EnumSet.of(
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
    public static final Set<Material> ITEMS_NAUTILUS_ARMORS = EnumSet.of(
            Material.COPPER_NAUTILUS_ARMOR,
            Material.IRON_NAUTILUS_ARMOR,
            Material.GOLDEN_NAUTILUS_ARMOR,
            Material.DIAMOND_NAUTILUS_ARMOR,
            Material.NETHERITE_NAUTILUS_ARMOR
    );

    /**
     * 所有种类的桶(除奶桶)
     */
    public static final Set<Material> ITEMS_BUCKETS = EnumSet.of(
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
     * 所有运输船(实体)
     */
    public static final Set<EntityType> ENTITY_TYPES_CHEST_BOATS = EnumSet.of(
            EntityType.OAK_CHEST_BOAT,
            EntityType.SPRUCE_CHEST_BOAT,
            EntityType.BIRCH_CHEST_BOAT,
            EntityType.JUNGLE_CHEST_BOAT,
            EntityType.ACACIA_CHEST_BOAT,
            EntityType.DARK_OAK_CHEST_BOAT,
            EntityType.PALE_OAK_CHEST_BOAT,
            EntityType.MANGROVE_CHEST_BOAT,
            EntityType.BAMBOO_CHEST_RAFT,
            EntityType.CHERRY_CHEST_BOAT
    );

    /**
     * 可被拴绳拴住的生物
     */
    public static final Set<EntityType> ENTITY_TYPES_CAN_BE_LEASHED = EnumSet.of(
            EntityType.ALLAY,
            EntityType.ARMADILLO,
            EntityType.AXOLOTL,
            EntityType.BEE,
            EntityType.CAMEL,
            EntityType.CAT,
            EntityType.CHICKEN,
            EntityType.COPPER_GOLEM,
            EntityType.COW,
            EntityType.DOLPHIN,
            EntityType.DONKEY,
            EntityType.FOX,
            EntityType.FROG,
            EntityType.GLOW_SQUID,
            EntityType.GOAT,
            EntityType.HAPPY_GHAST,
            EntityType.HOGLIN,
            EntityType.HORSE,
            EntityType.IRON_GOLEM,
            EntityType.LLAMA,
            EntityType.MOOSHROOM,
            EntityType.MULE,
            EntityType.OCELOT,
            EntityType.PARROT,
            EntityType.PIG,
            EntityType.POLAR_BEAR,
            EntityType.RABBIT,
            EntityType.SHEEP,
            EntityType.SKELETON_HORSE,
            EntityType.SNIFFER,
            EntityType.SNOW_GOLEM,
            EntityType.SQUID,
            EntityType.STRIDER,
            EntityType.TRADER_LLAMA,
            EntityType.ZOGLIN,
            EntityType.NAUTILUS,
            EntityType.WOLF,
            EntityType.ZOMBIE_NAUTILUS,
            EntityType.CAMEL_HUSK,
            EntityType.ZOMBIE_HORSE
    );
}
