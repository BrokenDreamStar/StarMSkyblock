package team.starm.starmskyblock.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import team.starm.starmskyblock.config.ConfigManager;
import team.starm.starmskyblock.generator.VoidChunkGenerator;
import team.starm.starmskyblock.message.MessageUtil;

/**
 * 空岛世界管理器。
 * 负责创建/加载三个虚空世界（主世界、下界、末地），
 * 使用 VoidChunkGenerator 生成仅含基岩和设定生物群系的虚空地形。
 * 世界名称、环境类型和生物群系均从 ConfigManager 读取。
 */
public class SkyblockWorldManager {

    private final ConfigManager configManager;
    private World skyblockWorld;
    private World skyblockNether;
    private World skyblockEnd;

    public SkyblockWorldManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public World getOrCreateSkyblockWorld() {
        if (skyblockWorld == null) {
            skyblockWorld = createWorld(configManager.getWorldNameNormal(), World.Environment.NORMAL,
                    configManager.getBiomeNormal(), Biome.PLAINS);
        }
        return skyblockWorld;
    }

    public World getOrCreateSkyblockNether() {
        if (skyblockNether == null) {
            skyblockNether = createWorld(configManager.getWorldNameNether(), World.Environment.NETHER,
                    configManager.getBiomeNether(), Biome.NETHER_WASTES);
        }
        return skyblockNether;
    }

    public World getOrCreateSkyblockEnd() {
        if (skyblockEnd == null) {
            skyblockEnd = createWorld(configManager.getWorldNameEnd(), World.Environment.THE_END,
                    configManager.getBiomeEnd(), Biome.THE_END);
        }
        return skyblockEnd;
    }

    @SuppressWarnings({"deprecation"})
    private World createWorld(String worldName, World.Environment environment, String biomeName, Biome defaultBiome) {
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
            java.io.File levelFile = new java.io.File(worldFolder, "level.dat");
            boolean existingData = worldFolder.isDirectory() && levelFile.isFile();

            if (existingData) {
                MessageUtil.consolePrint("正在加载已有空岛世界: " + worldName);
            } else {
                MessageUtil.consolePrint("正在创建空岛世界: " + worldName);
            }

            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(environment);
            creator.type(WorldType.NORMAL);
            creator.generateStructures(false);

            Biome biome = defaultBiome;
            try {
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(biomeName.toLowerCase());
                Biome configBiome = org.bukkit.Registry.BIOME.get(key);
                if (configBiome != null) {
                    biome = configBiome;
                } else {
                    MessageUtil.consoleWarn("未知的生物群系配置: " + biomeName + "，将使用默认的 " + defaultBiome.getKey().getKey());
                }
            } catch (Exception e) {
                MessageUtil.consoleError("解析生物群系配置时发生错误: " + biomeName + "，将使用默认的 " + defaultBiome.getKey().getKey());
            }
            creator.generator(new VoidChunkGenerator(biome));

            world = Bukkit.createWorld(creator);

            if (world != null) {
                if (!existingData) {
                    world.setSpawnLocation(0, 80, 0);
                }
                MessageUtil.consolePrint("空岛世界准备就绪！名称: " + worldName);
            } else {
                MessageUtil.consoleError("空岛世界加载或创建失败: " + worldName);
            }
        }

        return world;
    }

    public World getSkyblockWorld() {
        return skyblockWorld;
    }

    public World getSkyblockNether() {
        return skyblockNether;
    }

    public World getSkyblockEnd() {
        return skyblockEnd;
    }

    /**
     * 判断是否为空岛世界（主世界/下界/末地）
     */
    public boolean isSkyblockWorld(World world) {
        if (world == null) return false;
        return world.equals(skyblockWorld) || world.equals(skyblockNether) || world.equals(skyblockEnd);
    }

    /**
     * 判断世界名称是否为空岛世界
     */
    public boolean isSkyblockWorldName(String worldName) {
        if (worldName == null) return false;
        return worldName.equals(configManager.getWorldNameNormal())
                || worldName.equals(configManager.getWorldNameNether())
                || worldName.equals(configManager.getWorldNameEnd());
    }

    /**
     * 判断世界名称是否为空岛世界的某个环境
     */
    public boolean isNormalWorld(String worldName) {
        return worldName != null && worldName.equals(configManager.getWorldNameNormal());
    }

    public boolean isNetherWorld(String worldName) {
        return worldName != null && worldName.equals(configManager.getWorldNameNether());
    }

    public boolean isEndWorld(String worldName) {
        return worldName != null && worldName.equals(configManager.getWorldNameEnd());
    }
}
