package team.starm.starmskyblock.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;

import java.util.ArrayList;
import java.util.List;

public enum SkyblockBiome {

    // ====================== 主世界 ======================
    PLAINS("平原", Dimension.OVERWORLD, "&a"),
    SUNFLOWER_PLAINS("向日葵平原", Dimension.OVERWORLD, "&e"),
    DESERT("沙漠", Dimension.OVERWORLD, "&e"),
    SAVANNA("热带草原", Dimension.OVERWORLD, "&6"),
    SAVANNA_PLATEAU("热带高原", Dimension.OVERWORLD, "&6"),
    WINDSWEPT_SAVANNA("风袭热带草原", Dimension.OVERWORLD, "&6"),
    FOREST("森林", Dimension.OVERWORLD, "&a"),
    FLOWER_FOREST("繁花森林", Dimension.OVERWORLD, "&c"),
    BIRCH_FOREST("白桦森林", Dimension.OVERWORLD, "&f"),
    OLD_GROWTH_BIRCH_FOREST("原始白桦森林", Dimension.OVERWORLD, "&f"),
    DARK_FOREST("黑森林", Dimension.OVERWORLD, "&5"),
    OLD_GROWTH_PINE_TAIGA("原始松木针叶林", Dimension.OVERWORLD, "&6"),
    OLD_GROWTH_SPRUCE_TAIGA("原始云杉针叶林", Dimension.OVERWORLD, "&6"),
    TAIGA("针叶林", Dimension.OVERWORLD, "&2"),
    SNOWY_TAIGA("积雪针叶林", Dimension.OVERWORLD, "&f"),
    SNOWY_PLAINS("雪原", Dimension.OVERWORLD, "&b"),
    ICE_SPIKES("冰刺平原", Dimension.OVERWORLD, "&b"),
    JUNGLE("丛林", Dimension.OVERWORLD, "&2"),
    BAMBOO_JUNGLE("竹林", Dimension.OVERWORLD, "&2"),
    SPARSE_JUNGLE("稀疏丛林", Dimension.OVERWORLD, "&2"),
    BEACH("沙滩", Dimension.OVERWORLD, "&6"),
    SNOWY_BEACH("积雪沙滩", Dimension.OVERWORLD, "&f"),
    STONY_SHORE("石岸", Dimension.OVERWORLD, "&8"),
    SWAMP("沼泽", Dimension.OVERWORLD, "&7"),
    MANGROVE_SWAMP("红树林沼泽", Dimension.OVERWORLD, "&8"),
    RIVER("河流", Dimension.OVERWORLD, "&9"),
    FROZEN_RIVER("冻河", Dimension.OVERWORLD, "&b"),
    MUSHROOM_FIELDS("蘑菇岛", Dimension.OVERWORLD, "&5"),
    CHERRY_GROVE("樱花树林", Dimension.OVERWORLD, "&d"),
    OCEAN("海洋", Dimension.OVERWORLD, "&9"),
    DEEP_OCEAN("深海", Dimension.OVERWORLD, "&9"),
    COLD_OCEAN("冷水海洋", Dimension.OVERWORLD, "&1"),
    DEEP_COLD_OCEAN("冷水深海", Dimension.OVERWORLD, "&1"),
    FROZEN_OCEAN("冻洋", Dimension.OVERWORLD, "&b"),
    DEEP_FROZEN_OCEAN("深海冻洋", Dimension.OVERWORLD, "&b"),
    LUKEWARM_OCEAN("温水海洋", Dimension.OVERWORLD, "&3"),
    DEEP_LUKEWARM_OCEAN("温水深海", Dimension.OVERWORLD, "&3"),
    WARM_OCEAN("暖水海洋", Dimension.OVERWORLD, "&e"),
    DEEP_WARM_OCEAN("深海暖水海洋", Dimension.OVERWORLD, "&e"),
    MEADOW("草甸", Dimension.OVERWORLD, "&a"),
    WINDSWEPT_HILLS("风袭丘陵", Dimension.OVERWORLD, "&2"),
    WINDSWEPT_GRAVELLY_HILLS("风袭砾石丘陵", Dimension.OVERWORLD, "&7"),
    WINDSWEPT_FOREST("风袭森林", Dimension.OVERWORLD, "&2"),
    STONY_PEAKS("石峰", Dimension.OVERWORLD, "&7"),
    FROZEN_PEAKS("冰封山峰", Dimension.OVERWORLD, "&b"),
    JAGGED_PEAKS("尖峭山峰", Dimension.OVERWORLD, "&f"),
    SNOWY_SLOPES("雪坡", Dimension.OVERWORLD, "&f"),
    GROVE("雪林", Dimension.OVERWORLD, "&f"),
    DEEP_DARK("深暗之域", Dimension.OVERWORLD, "&d"),
    DRIPSTONE_CAVES("溶洞", Dimension.OVERWORLD, "&6"),
    LUSH_CAVES("繁茂洞穴", Dimension.OVERWORLD, "&a"),
    BADLANDS("恶地", Dimension.OVERWORLD, "&4"),
    WOODED_BADLANDS("林地恶地", Dimension.OVERWORLD, "&c"),
    ERODED_BADLANDS("风蚀恶地", Dimension.OVERWORLD, "&4"),

    // ====================== 下界 ======================
    NETHER_WASTES("下界荒地", Dimension.NETHER, "&c"),
    CRIMSON_FOREST("绯红森林", Dimension.NETHER, "&4"),
    WARPED_FOREST("诡异森林", Dimension.NETHER, "&3"),
    SOUL_SAND_VALLEY("灵魂沙峡谷", Dimension.NETHER, "&3"),
    BASALT_DELTAS("玄武岩三角洲", Dimension.NETHER, "&7");

    public enum Dimension {
        OVERWORLD, NETHER
    }

    private final String displayName;
    private final Dimension dimension;
    private final String colorPrefix;

    SkyblockBiome(String displayName, Dimension dimension, String colorPrefix) {
        this.displayName = displayName;
        this.dimension = dimension;
        this.colorPrefix = colorPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColoredDisplayName() {
        return colorPrefix + "&l" + displayName;
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
