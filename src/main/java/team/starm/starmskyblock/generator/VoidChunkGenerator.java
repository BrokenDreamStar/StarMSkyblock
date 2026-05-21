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
 * 自定义虚空生成器 - 所有区块都是空气（适合空岛插件）
 */
public class VoidChunkGenerator extends ChunkGenerator {

    private final Biome biome;

    public VoidChunkGenerator(Biome biome) {
        this.biome = biome;
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
            @NotNull ChunkData chunkData) {
        // 什么都不做 = 纯虚空
        // 不填充任何方块
    }

    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z,
            @NotNull HeightMap heightMap) {
        return 0; // 地面高度为 0（但我们不生成地面）
    }

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
