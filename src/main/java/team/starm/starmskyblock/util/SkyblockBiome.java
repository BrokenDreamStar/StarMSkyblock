package team.starm.starmskyblock.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;

import java.util.ArrayList;
import java.util.List;

public enum SkyblockBiome {

    // ====================== 主世界 ======================
    PLAINS("平原", Dimension.OVERWORLD),
    SUNFLOWER_PLAINS("向日葵平原", Dimension.OVERWORLD),
    DESERT("沙漠", Dimension.OVERWORLD),
    SAVANNA("热带草原", Dimension.OVERWORLD),
    SAVANNA_PLATEAU("热带高原", Dimension.OVERWORLD),
    WINDSWEPT_SAVANNA("风袭热带草原", Dimension.OVERWORLD),
    FOREST("森林", Dimension.OVERWORLD),
    FLOWER_FOREST("繁花森林", Dimension.OVERWORLD),
    BIRCH_FOREST("白桦森林", Dimension.OVERWORLD),
    OLD_GROWTH_BIRCH_FOREST("原始白桦森林", Dimension.OVERWORLD),
    DARK_FOREST("黑森林", Dimension.OVERWORLD),
    OLD_GROWTH_PINE_TAIGA("原始松木针叶林", Dimension.OVERWORLD),
    OLD_GROWTH_SPRUCE_TAIGA("原始云杉针叶林", Dimension.OVERWORLD),
    TAIGA("针叶林", Dimension.OVERWORLD),
    SNOWY_TAIGA("积雪针叶林", Dimension.OVERWORLD),
    SNOWY_PLAINS("雪原", Dimension.OVERWORLD),
    ICE_SPIKES("冰刺平原", Dimension.OVERWORLD),
    JUNGLE("丛林", Dimension.OVERWORLD),
    BAMBOO_JUNGLE("竹林", Dimension.OVERWORLD),
    SPARSE_JUNGLE("稀疏丛林", Dimension.OVERWORLD),
    BEACH("沙滩", Dimension.OVERWORLD),
    SNOWY_BEACH("积雪沙滩", Dimension.OVERWORLD),
    STONY_SHORE("石岸", Dimension.OVERWORLD),
    SWAMP("沼泽", Dimension.OVERWORLD),
    MANGROVE_SWAMP("红树林沼泽", Dimension.OVERWORLD),
    RIVER("河流", Dimension.OVERWORLD),
    FROZEN_RIVER("冻河", Dimension.OVERWORLD),
    MUSHROOM_FIELDS("蘑菇岛", Dimension.OVERWORLD),
    CHERRY_GROVE("樱花树林", Dimension.OVERWORLD),
    OCEAN("海洋", Dimension.OVERWORLD),
    DEEP_OCEAN("深海", Dimension.OVERWORLD),
    COLD_OCEAN("冷水海洋", Dimension.OVERWORLD),
    DEEP_COLD_OCEAN("冷水深海", Dimension.OVERWORLD),
    FROZEN_OCEAN("冻洋", Dimension.OVERWORLD),
    DEEP_FROZEN_OCEAN("深海冻洋", Dimension.OVERWORLD),
    LUKEWARM_OCEAN("温水海洋", Dimension.OVERWORLD),
    DEEP_LUKEWARM_OCEAN("温水深海", Dimension.OVERWORLD),
    WARM_OCEAN("暖水海洋", Dimension.OVERWORLD),
    DEEP_WARM_OCEAN("深海暖水海洋", Dimension.OVERWORLD),
    MEADOW("草甸", Dimension.OVERWORLD),
    WINDSWEPT_HILLS("风袭丘陵", Dimension.OVERWORLD),
    WINDSWEPT_GRAVELLY_HILLS("风袭砾石丘陵", Dimension.OVERWORLD),
    WINDSWEPT_FOREST("风袭森林", Dimension.OVERWORLD),
    STONY_PEAKS("石峰", Dimension.OVERWORLD),
    FROZEN_PEAKS("冰封山峰", Dimension.OVERWORLD),
    JAGGED_PEAKS("尖峭山峰", Dimension.OVERWORLD),
    SNOWY_SLOPES("雪坡", Dimension.OVERWORLD),
    GROVE("雪林", Dimension.OVERWORLD),
    DEEP_DARK("深暗之域", Dimension.OVERWORLD),
    DRIPSTONE_CAVES("溶洞", Dimension.OVERWORLD),
    LUSH_CAVES("繁茂洞穴", Dimension.OVERWORLD),
    BADLANDS("恶地", Dimension.OVERWORLD),
    WOODED_BADLANDS("林地恶地", Dimension.OVERWORLD),
    ERODED_BADLANDS("风蚀恶地", Dimension.OVERWORLD),

    // ====================== 下界 ======================
    NETHER_WASTES("下界荒地", Dimension.NETHER),
    CRIMSON_FOREST("绯红森林", Dimension.NETHER),
    WARPED_FOREST("诡异森林", Dimension.NETHER),
    SOUL_SAND_VALLEY("灵魂沙峡谷", Dimension.NETHER),
    BASALT_DELTAS("玄武岩三角洲", Dimension.NETHER);

    public enum Dimension {
        OVERWORLD, NETHER
    }

    private final String displayName;
    private final Dimension dimension;

    SkyblockBiome(String displayName, Dimension dimension) {
        this.displayName = displayName;
        this.dimension = dimension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public Biome toBukkitBiome() {
        NamespacedKey key = NamespacedKey.minecraft(name().toLowerCase());
        Biome biome = Registry.BIOME.get(key);
        return biome;
    }

    public static SkyblockBiome fromInput(String input) {
        for (SkyblockBiome biome : values()) {
            if (biome.name().equalsIgnoreCase(input) || biome.displayName.equals(input)) {
                return biome;
            }
        }
        return null;
    }

    public static List<SkyblockBiome> valuesFor(Dimension dimension) {
        List<SkyblockBiome> result = new ArrayList<>();
        for (SkyblockBiome biome : values()) {
            if (biome.dimension == dimension) {
                result.add(biome);
            }
        }
        return result;
    }

    public static List<String> namesFor(Dimension dimension) {
        List<String> result = new ArrayList<>();
        for (SkyblockBiome biome : values()) {
            if (biome.dimension == dimension) {
                result.add(biome.name());
            }
        }
        return result;
    }

    public static List<String> displayNamesFor(Dimension dimension) {
        List<String> result = new ArrayList<>();
        for (SkyblockBiome biome : values()) {
            if (biome.dimension == dimension) {
                result.add(biome.displayName);
            }
        }
        return result;
    }
}
