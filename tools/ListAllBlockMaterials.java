package team.starm.starmskyblock.util;

import org.bukkit.Material;
import java.lang.reflect.Field;

/**
 * 临时工具 —— 使用反射检查 Material 是否为方块
 */
public class ListAllBlockMaterials {

    public static void main(String[] args) throws Exception {
        // 尝试另一种方式：反射读取 key 字段并检查 NamespacedKey
        int total = 0;
        int blocks = 0;
        int nonBlocks = 0;

        // Material 的 isBlock 在运行时失败，用 isItem 辅助判断
        // 在 Paper 1.21 中，只有方块材料才有非空的 blockType
        // 我们尝试通过 Material 常量名来判断
        for (Material material : Material.values()) {
            if (material.isLegacy()) continue;
            total++;

            String name = material.name();

            // 已知非方块物品
            if (isNonBlockItem(name)) {
                nonBlocks++;
                // 输出非方块以供验证
                System.err.println("SKIP: " + name);
                continue;
            }

            // 已知方块
            blocks++;
            System.out.println(name);
        }
        System.err.println("=== Total: " + total + ", Blocks: " + blocks + ", Non-blocks: " + nonBlocks + " ===");
    }

    static boolean isNonBlockItem(String name) {
        // Spawn eggs
        if (name.endsWith("_SPAWN_EGG")) return true;
        // Potions
        if (name.equals("POTION") || name.equals("LINGERING_POTION") || name.equals("SPLASH_POTION")) return true;
        // Minecarts
        if (name.endsWith("_MINECART") || name.equals("MINECART")) return true;
        // Boats
        if (name.endsWith("_RAFT") || name.endsWith("_BOAT")) return true;
        // Armor
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) return true;
        if (name.equals("TURTLE_HELMET") || name.equals("WOLF_ARMOR")) return true;
        // Tools/Weapons
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) return true;
        // Horse armor
        if (name.endsWith("_HORSE_ARMOR")) return true;
        // Equipment/Items
        if (name.equals("ELYTRA") || name.equals("SHIELD") || name.equals("FISHING_ROD") || name.equals("FLINT_AND_STEEL") || name.equals("SHEARS")) return true;
        if (name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT") || name.equals("MACE") || name.equals("BRUSH")) return true;
        if (name.equals("LEAD") || name.equals("COMPASS") || name.equals("RECOVERY_COMPASS") || name.equals("CLOCK")) return true;
        if (name.equals("SPYGLASS") || name.equals("GOAT_HORN")) return true;
        if (name.equals("BOWL") || name.equals("STICK") || name.equals("BLAZE_ROD") || name.equals("BREEZE_ROD")) return true;
        if (name.equals("ENDER_PEARL") || name.equals("ENDER_EYE") || name.equals("EXPERIENCE_BOTTLE")) return true;
        if (name.equals("FIREWORK_ROCKET") || name.equals("FIREWORK_STAR")) return true;
        if (name.contains("BANNER_PATTERN")) return true;
        if (name.startsWith("MUSIC_DISC_") || name.equals("NAME_TAG") || name.equals("SADDLE")) return true;
        if (name.equals("NAUTILUS_SHELL") || name.equals("HEART_OF_THE_SEA") || name.equals("TOTEM_OF_UNDYING")) return true;
        if (name.equals("BOOK") || name.equals("WRITABLE_BOOK") || name.equals("WRITTEN_BOOK")) return true;
        if (name.equals("PAPER") || name.equals("MAP") || name.equals("FILLED_MAP") || name.equals("KNOWLEDGE_BOOK")) return true;
        if (name.equals("DEBUG_STICK") || name.equals("CARROT_ON_A_STICK") || name.equals("WARPED_FUNGUS_ON_A_STICK")) return true;
        if (name.equals("ENCHANTED_BOOK") || name.equals("DRAGON_BREATH") || name.equals("GLASS_BOTTLE") || name.equals("EXPERIENCE_BOTTLE")) return true;
        if (name.equals("HONEY_BOTTLE") || name.equals("MILK_BUCKET") || name.equals("WATER_BUCKET") || name.equals("LAVA_BUCKET") || name.equals("PUFFERFISH_BUCKET") || name.equals("SALMON_BUCKET") || name.equals("COD_BUCKET") || name.equals("TROPICAL_FISH_BUCKET") || name.equals("AXOLOTL_BUCKET") || name.equals("TADPOLE_BUCKET") || name.equals("POWDER_SNOW_BUCKET")) return true;
        if (name.equals("BUCKET") || name.equals("BONE_MEAL") || name.equals("EGG") || name.equals("SNOWBALL") || name.equals("ARMOR_STAND") || name.equals("PAINTING") || name.equals("ITEM_FRAME") || name.equals("GLOW_ITEM_FRAME")) return true;
        if (name.equals("INK_SAC") || name.equals("GLOW_INK_SAC") || name.equals("COCOA_BEANS") || name.equals("LAPIS_LAZULI")) return true;
        if (name.equals("DISC_FRAGMENT_5") || name.equals("NETHER_STAR") || name.equals("SHULKER_SHELL")) return true;
        if (name.equals("STRING") || name.equals("FEATHER") || name.equals("GUNPOWDER") || name.equals("FLINT") || name.equals("LEATHER")) return true;
        if (name.equals("RABBIT_HIDE") || name.equals("RABBIT_FOOT") || name.equals("SCUTE") || name.equals("AMETHYST_SHARD")) return true;
        if (name.equals("PHANTOM_MEMBRANE") || name.equals("PRISMARINE_SHARD") || name.equals("PRISMARINE_CRYSTALS") || name.equals("RAW_IRON") || name.equals("RAW_GOLD") || name.equals("RAW_COPPER")) return true;
        if (name.equals("RABBIT_STEW") || name.equals("BEETROOT_SOUP") || name.equals("MUSHROOM_STEW") || name.equals("SUSPICIOUS_STEW")) return true;
        if (name.equals("BAKED_POTATO") || name.equals("POISONOUS_POTATO") || name.equals("GOLDEN_CARROT") || name.equals("GOLDEN_APPLE") || name.equals("ENCHANTED_GOLDEN_APPLE")) return true;
        if (name.equals("OMINOUS_BOTTLE") || name.equals("TRIAL_KEY") || name.equals("OMINOUS_TRIAL_KEY") || name.equals("WIND_CHARGE")) return true;
        if (name.equals("ROTTEN_FLESH") || name.equals("SPIDER_EYE") || name.equals("FERMENTED_SPIDER_EYE") || name.equals("MAGMA_CREAM") || name.equals("GHAST_TEAR")) return true;
        if (name.equals("GLISTERING_MELON_SLICE") || name.equals("BLAZE_POWDER") || name.equals("SUGAR") || name.equals("GOLD_NUGGET")) return true;
        if (name.equals("OMINOUS_TRIAL_KEY")) return true;
        // Food items
        if (name.equals("APPLE") || name.equals("BREAD") || name.equals("COOKED_BEEF") || name.equals("COOKED_PORKCHOP") || name.equals("COOKED_CHICKEN") || name.equals("COOKED_COD") || name.equals("COOKED_SALMON") || name.equals("COOKED_MUTTON") || name.equals("COOKED_RABBIT")) return true;
        if (name.equals("BEEF") || name.equals("PORKCHOP") || name.equals("CHICKEN") || name.equals("COD") || name.equals("SALMON") || name.equals("MUTTON") || name.equals("RABBIT")) return true;
        if (name.equals("DRIED_KELP") || name.equals("PUMPKIN_PIE") || name.equals("COOKIE") || name.equals("CAKE") || name.equals("CHORUS_FRUIT") || name.equals("POPPED_CHORUS_FRUIT")) return true;
        if (name.equals("SWEET_BERRIES") || name.equals("GLOW_BERRIES") || name.equals("MELON_SLICE") || name.equals("CARROT") || name.equals("POTATO") || name.equals("BEETROOT") || name.equals("WHEAT")) return true;
        return false;
    }
}