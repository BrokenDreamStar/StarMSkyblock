package team.starm.starmskyblock.tag;

import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Set;

/**
 * 实体标签工具类
 *
 * 预定义了一系列 EntityType 集合，用于方便地判断实体是否属于某种分类。
 * 涵盖运输船、可骑乘生物、可拴绳生物、携带背包的骑乘生物等常见分类，
 * 避免在业务代码中反复编写 instanceof 判断或硬编码的实体列表
 */
public class EntityTags {
    /**
     * 所有种类的运输船（带箱子的船/筏）
     * 用于判断实体是否为可存储物品的水上交通工具
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
     * 可被玩家骑乘的生物类型集合
     * 包括马、驴、骡、猪、炽足兽、骆驼、鹦鹉螺等
     * 用于判断玩家是否可骑上该生物（通过右键交互）
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

    /**
     * 携带背包（额外物品栏）的可骑乘生物集合
     * 这类生物除了可被骑乘外，还拥有独立的物品栏，
     * 可以装备鞍具、箱子（驴/骡）、地毯（羊驼）等
     * 用于打开生物背包 UI 时的判断
     */
    public static final Set<EntityType> ANIMALS_WITH_INVENTORY = EnumSet.of(
            EntityType.HORSE,
            EntityType.DONKEY,
            EntityType.MULE,
            EntityType.LLAMA,
            EntityType.SKELETON_HORSE,
            EntityType.ZOMBIE_HORSE,
            EntityType.CAMEL,
            EntityType.NAUTILUS,
            EntityType.ZOMBIE_NAUTILUS
    );
}
