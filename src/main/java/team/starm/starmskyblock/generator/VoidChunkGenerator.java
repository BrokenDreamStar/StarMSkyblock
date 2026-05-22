package team.starm.starmskyblock.generator;

import org.bukkit.HeightMap;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 自定义虚空生成器 —— 所有区块全部留空（纯空气），不产生任何自然地形。
 * <p>
 * 这是空岛玩法的核心：所有方块都由岛屿结构文件（schematic）手动粘贴，
 * 世界不生成地底矿石、树木等任何自然资源。
 */
public class VoidChunkGenerator extends ChunkGenerator {

    /** 整个世界的统一生物群系（避免不同区块群系差异导致视觉不连贯） */
    private final Biome biome;

    public VoidChunkGenerator(Biome biome) {
        this.biome = biome;
    }

    /** 不生成任何噪声方块 → 区块内全部为空气 */
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
            @NotNull ChunkData chunkData) {
        // 什么都不做 = 纯虚空
        // 不填充任何方块
    }

    /** 返回地面高度 0（空岛世界不需要自然地面高度） */
    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z,
            @NotNull HeightMap heightMap) {
        return 0; // 地面高度为 0（但我们不生成地面）
    }

    /** 为整个世界提供唯一的恒定生物群系，避免 Mojang 默认群系混合 */
    @Nullable
    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return new BiomeProvider() {
            @NotNull
            @Override
            public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
                return biome;
            }

            @NotNull
            @Override
            public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
                return Collections.singletonList(biome);
            }
        };
    }
}
