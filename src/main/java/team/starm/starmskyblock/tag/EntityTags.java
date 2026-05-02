package team.starm.starmskyblock.tag;

import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Set;

public class EntityTags {
    /**
     * 所有种类的运输船(实体)
     */
    public static final Set<EntityType> CHEST_BOATS = EnumSet.of(
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
     * 可被骑乘的生物
     */
    public static final Set<EntityType> RIDEABLE = EnumSet.of(
            EntityType.HORSE,
            EntityType.DONKEY,
            EntityType.MULE,
            EntityType.SKELETON_HORSE,
            EntityType.ZOMBIE_HORSE,
            EntityType.NAUTILUS,
            EntityType.ZOMBIE_NAUTILUS,
            EntityType.PIG,
            EntityType.STRIDER,
            EntityType.LLAMA,
            EntityType.TRADER_LLAMA,
            EntityType.CAMEL,
            EntityType.CAMEL_HUSK,
            EntityType.HAPPY_GHAST
    );

    /**
     * 可被拴绳拴住的生物
     */
    public static final Set<EntityType> CAN_BE_LEASHED = EnumSet.of(
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
