package team.starm.starmskyblock.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.generator.VoidChunkGenerator;
import team.starm.starmskyblock.util.ColorUtil;

public class SkyblockWorldManager {

    private final StarMSkyblock plugin;
    private World skyblockWorld;
    private World skyblockNether;
    private World skyblockEnd;

    public SkyblockWorldManager(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    public World getOrCreateSkyblockWorld() {
        if (skyblockWorld == null) {
            skyblockWorld = createWorld(plugin.getConfigManager().getWorldNameNormal(), World.Environment.NORMAL,
                    plugin.getConfigManager().getBiomeNormal(), Biome.PLAINS);
        }
        return skyblockWorld;
    }

    public World getOrCreateSkyblockNether() {
        if (skyblockNether == null) {
            skyblockNether = createWorld(plugin.getConfigManager().getWorldNameNether(), World.Environment.NETHER,
                    plugin.getConfigManager().getBiomeNether(), Biome.NETHER_WASTES);
        }
        return skyblockNether;
    }

    public World getOrCreateSkyblockEnd() {
        if (skyblockEnd == null) {
            skyblockEnd = createWorld(plugin.getConfigManager().getWorldNameEnd(), World.Environment.THE_END,
                    plugin.getConfigManager().getBiomeEnd(), Biome.THE_END);
        }
        return skyblockEnd;
    }

    @SuppressWarnings({"deprecation"})
    private World createWorld(String worldName, World.Environment environment, String biomeName, Biome defaultBiome) {
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists() && worldFolder.isDirectory()) {
                ColorUtil.consolePrint("&e检测到已存在的空岛世界数据，正在加载 " + worldName + "...");
            } else {
                ColorUtil.consolePrint("&e正在创建新的空岛虚空世界: " + worldName);
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
                    ColorUtil.consoleError("&e未知的生物群系配置: " + biomeName + "，将使用默认的 " + defaultBiome.getKey().getKey());
                }
            } catch (Exception e) {
                ColorUtil.consoleError("&e解析生物群系配置时发生错误: " + biomeName + "，将使用默认的 " + defaultBiome.getKey().getKey());
            }
            creator.generator(new VoidChunkGenerator(biome));

            world = Bukkit.createWorld(creator);

            if (world != null) {
                if (!worldFolder.exists() || !(new java.io.File(worldFolder, "level.dat")).exists()) {
                    world.setSpawnLocation(0, 80, 0);
                }
                ColorUtil.consolePrint("&a空岛世界准备就绪！名称: " + worldName);
            } else {
                ColorUtil.consoleError("&c空岛世界加载或创建失败: " + worldName);
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
}